<?php
/**
 * AI Agent 核心调度类
 * 负责与大模型交互、注册工具、调度执行、汇总结果
 */
class Agent {
    private $config;
    private $tools = [];
    private $toolCallbacks = [];
    private $callLogId = 0;
    private $userId;

    public function __construct($userId = 0) {
        require_once __DIR__ . '/../../config/ai.php';
        $this->config = get_ai_config();
        $this->userId = $userId;
        if (!$this->config) {
            throw new Exception('AI 服务未配置，请在管理后台配置 AI 服务商和 API Key');
        }
    }

    /**
     * 注册工具
     */
    public function registerTool($name, $description, $parameters, $callback) {
        $this->tools[] = [
            'type' => 'function',
            'function' => [
                'name' => $name,
                'description' => $description,
                'parameters' => $parameters,
            ]
        ];
        $this->toolCallbacks[$name] = $callback;
    }

    /**
     * 执行 AI 识别
     * @param string $imageUrl 图片URL或本地路径
     * @param array|null $customCategories APP传来的自定义分类列表
     * @return array 结构化识别结果
     */
    public function recognize($imageUrl, $customCategories = null) {
        $startTime = microtime(true);

        // 创建调用日志
        $this->callLogId = $this->createCallLog($imageUrl);

        try {
            $systemPrompt = get_ai_system_prompt($customCategories);

            // 将图片转为 base64 data URL（确保AI模型能访问）
            $imageDataUrl = $imageUrl;
            error_log('Agent: imageUrl=' . $imageUrl);
            if (strpos($imageUrl, 'http') === 0) {
                // 是URL，尝试下载转base64
                $localPath = null;
                // 尝试从URL中提取本地路径
                $uploadPrefix = IMAGE_URL_PREFIX ?? '';
                if (!empty($uploadPrefix) && strpos($imageUrl, $uploadPrefix) === 0) {
                    $relative = substr($imageUrl, strlen($uploadPrefix));
                    $localPath = UPLOAD_PATH . $relative;
                }
                if ($localPath && file_exists($localPath)) {
                    // 本地文件直接读取
                    $imgData = file_get_contents($localPath);
                } else {
                    // 下载远程图片
                    $imgData = @file_get_contents($imageUrl);
                }
                if ($imgData) {
                    // 压缩图片：最大1600px，质量75%，减少API超时
                    $imgData = $this->compressImage($imgData, 1600, 75);
                    $mime = $this->detectMime($imgData, $imageUrl);
                    $imageDataUrl = 'data:' . $mime . ';base64,' . base64_encode($imgData);
                    error_log('Agent: imgDataLen=' . strlen($imgData) . ' mime=' . $mime . ' dataUrlLen=' . strlen($imageDataUrl));
                } else {
                    error_log('Agent: imgData is empty! localPath=' . ($localPath ?? 'null') . ' exists=' . (file_exists($localPath ?? '') ? 'yes' : 'no'));
                }
            } elseif (file_exists($imageUrl)) {
                // 是本地文件路径
                $imgData = file_get_contents($imageUrl);
                if ($imgData) {
                    $imgData = $this->compressImage($imgData, 1600, 75);
                    $mime = $this->detectMime($imgData, $imageUrl);
                    $imageDataUrl = 'data:' . $mime . ';base64,' . base64_encode($imgData);
                }
            }

            $messages = [
                ['role' => 'system', 'content' => $systemPrompt],
                [
                    'role' => 'user',
                    'content' => [
                        ['type' => 'image_url', 'image_url' => ['url' => $imageDataUrl]],
                        ['type' => 'text', 'text' => '请识别这张图片中的物品，按系统提示的JSON格式返回结果。'],
                    ]
                ]
            ];

            $payload = [
                'model' => $this->config['model'],
                'messages' => $messages,
                'temperature' => 0.2,
                'max_tokens' => 500,
            ];

            // 只有配置了 tools 且模型可能支持时才发送
            if (!empty($this->tools)) {
                $payload['tools'] = $this->tools;
                $payload['tool_choice'] = 'auto';
            }

            // 第一次调用大模型
            $response = $this->callApi($payload);
            $usage = $response['usage'] ?? [];

            // 处理工具调用循环（最多 3 轮，防止死循环）
            $maxRounds = 3;
            $round = 0;
            while (isset($response['choices'][0]['message']['tool_calls']) && $round < $maxRounds) {
                $round++;
                $toolCalls = $response['choices'][0]['message']['tool_calls'];
                $messages[] = $response['choices'][0]['message'];

                foreach ($toolCalls as $call) {
                    $toolName = $call['function']['name'];
                    $toolArgs = json_decode($call['function']['arguments'], true) ?: [];
                    $toolStartTime = microtime(true);

                    $result = $this->executeTool($toolName, $toolArgs);

                    $toolDuration = intval((microtime(true) - $toolStartTime) * 1000);
                    $this->logToolCall($this->callLogId, $toolName, $toolArgs, $result, $toolDuration);

                    $messages[] = [
                        'role' => 'tool',
                        'tool_call_id' => $call['id'],
                        'content' => json_encode($result, JSON_UNESCAPED_UNICODE),
                    ];
                }

                // 二次调用获取最终结果
                $payload['messages'] = $messages;
                $response = $this->callApi($payload);
                if (isset($response['usage'])) {
                    $usage = $response['usage'];
                }
            }

            $duration = intval((microtime(true) - $startTime) * 1000);
            $message = $response['choices'][0]['message'] ?? [];
            $content = $message['content'] ?? '';

            // 如果 content 为空，尝试从 reasoning_content 提取
            if (empty($content) && !empty($message['reasoning_content'])) {
                $content = $message['reasoning_content'];
            }

            // 移除 <think>...</think> 标签及其内容
            $content = preg_replace('/<think>[\s\S]*?<\/think>/i', '', $content);
            $content = trim($content);

            // 解析 JSON 结果
            $result = $this->parseResult($content);

            // 更新日志
            $this->updateCallLog($this->callLogId, [
                'prompt_tokens'     => $usage['prompt_tokens'] ?? 0,
                'completion_tokens' => $usage['completion_tokens'] ?? 0,
                'total_tokens'      => $usage['total_tokens'] ?? 0,
                'status'            => 1,
                'duration'          => $duration,
            ]);

            // 更新 API 调用统计
            $this->updateApiStats($this->config['id'], true);

            return $result;

        } catch (Exception $e) {
            $duration = intval((microtime(true) - $startTime) * 1000);
            $this->updateCallLog($this->callLogId, [
                'status'   => 0,
                'error_msg'=> $e->getMessage(),
                'duration' => $duration,
            ]);
            $this->updateApiStats($this->config['id'], false);
            throw $e;
        }
    }

    /**
     * 调用大模型 API（统一入口，支持多服务商）
     */
    private function callApi($payload) {
        $provider = $this->config['provider'];
        $apiUrl = $this->config['api_url'];
        $apiKey = $this->config['api_key'];

        if (empty($apiKey)) {
            throw new Exception('AI API Key 未配置');
        }

        $headers = [
            'Content-Type: application/json',
        ];

        // 不同服务商的鉴权方式
        switch ($provider) {
            case 'zhipu':
                $headers[] = 'Authorization: Bearer ' . $apiKey;
                break;
            case 'doubao':
                $headers[] = 'Authorization: Bearer ' . $apiKey;
                break;
            case 'ernie':
                // 文心需要先获取 access_token，这里简化为直接用 API Key
                $headers[] = 'Authorization: Bearer ' . $apiKey;
                break;
            default:
                $headers[] = 'Authorization: Bearer ' . $apiKey;
        }

        $response = $this->httpPost($apiUrl, json_encode($payload), $headers);
        $data = json_decode($response, true);

        if (!$data) {
            throw new Exception('AI 服务返回无效数据: ' . substr($response, 0, 200));
        }

        if (isset($data['error'])) {
            $errMsg = $data['error']['message'] ?? $data['error']['msg'] ?? json_encode($data['error']);
            throw new Exception('AI 服务错误: ' . $errMsg);
        }

        if (!isset($data['choices'])) {
            throw new Exception('AI 服务返回格式异常');
        }

        return $data;
    }

    /**
     * 执行工具
     */
    private function executeTool($name, $args) {
        if (!isset($this->toolCallbacks[$name])) {
            return ['error' => "未知工具: $name"];
        }
        try {
            return call_user_func($this->toolCallbacks[$name], $args);
        } catch (Exception $e) {
            return ['error' => $e->getMessage()];
        }
    }

    /**
     * 解析大模型返回的 JSON 结果
     */
    private function parseResult($content) {
        $content = trim($content);

        // 记录原始内容用于调试
        $debugContent = substr($content, 0, 500);

        // 方法1: 直接解析
        $result = json_decode($content, true);
        if ($result && is_array($result) && $this->hasRecognizableFields($result)) {
            return $this->normalizeResult($result);
        }

        // 方法2: 从 markdown 代码块中提取
        if (preg_match('/```(?:json)?\s*\n?(.*?)\n?```/s', $content, $m)) {
            $inner = trim($m[1]);
            $result = json_decode($inner, true);
            if ($result && is_array($result)) {
                return $this->normalizeResult($result);
            }
        }

        // 方法3: 找所有可能的 JSON 对象（支持多个 {} 块）
        if (preg_match_all('/\{[^{}]*\}/s', $content, $matches)) {
            foreach ($matches[0] as $match) {
                $result = json_decode($match, true);
                if ($result && is_array($result) && $this->hasRecognizableFields($result)) {
                    return $this->normalizeResult($result);
                }
            }
        }

        // 方法4: 找第一个 { 到最后一个 }（处理嵌套JSON）
        $start = strpos($content, '{');
        $end = strrpos($content, '}');
        if ($start !== false && $end !== false && $end > $start) {
            $result = json_decode(substr($content, $start, $end - $start + 1), true);
            if ($result && is_array($result)) {
                return $this->normalizeResult($result);
            }
        }

        // 方法5: 提取 key-value 对重建（兼容中英文 key）
        $rebuild = [];
        // 英文 key
        if (preg_match('/"goods_name"\s*:\s*"([^"]+)"/', $content, $nm)) $rebuild['goods_name'] = $nm[1];
        if (preg_match('/"brand"\s*:\s*"([^"]*)"/', $content, $b)) $rebuild['brand'] = $b[1];
        if (preg_match('/"category"\s*:\s*"([^"]*)"/', $content, $c)) $rebuild['category'] = $c[1];
        if (preg_match('/"spec"\s*:\s*"([^"]*)"/', $content, $s)) $rebuild['spec'] = $s[1];
        if (preg_match('/"expire_date"\s*:\s*"([^"]*)"/', $content, $e)) $rebuild['expire_date'] = $e[1];
        if (preg_match('/"storage_tip"\s*:\s*"([^"]*)"/', $content, $t)) $rebuild['storage_tip'] = $t[1];
        if (preg_match('/"confidence"\s*:?\s*([\d.]+)/', $content, $cf)) $rebuild['confidence'] = $cf[1];
        if (preg_match('/"barcode"\s*:\s*"([^"]*)"/', $content, $bc)) $rebuild['barcode'] = $bc[1];
        // 中文 key（部分模型会返回中文字段名）
        if (preg_match('/"名称"\s*:\s*"([^"]+)"/u', $content, $nm2)) $rebuild['goods_name'] = $rebuild['goods_name'] ?? $nm2[1];
        if (preg_match('/"品牌"\s*:\s*"([^"]*)"/u', $content, $b2)) $rebuild['brand'] = $rebuild['brand'] ?? $b2[1];
        if (preg_match('/"分类"\s*:\s*"([^"]*)"/u', $content, $c2)) $rebuild['category'] = $rebuild['category'] ?? $c2[1];
        if (preg_match('/"规格"\s*:\s*"([^"]*)"/u', $content, $s2)) $rebuild['spec'] = $rebuild['spec'] ?? $s2[1];
        if (preg_match('/"条形码"\s*:\s*"([^"]*)"/u', $content, $bc2)) $rebuild['barcode'] = $rebuild['barcode'] ?? $bc2[1];
        if (preg_match('/"置信度"\s*:?\s*([\d.]+)/u', $content, $cf2)) $rebuild['confidence'] = $rebuild['confidence'] ?? $cf2[1];
        if (!empty($rebuild)) {
            return $this->normalizeResult($rebuild);
        }

        // 方法6: 按行解析 key: value 格式（有些模型返回纯文本键值对）
        $lines = explode("\n", $content);
        $kvResult = [];
        foreach ($lines as $line) {
            $line = trim($line);
            if (preg_match('/^(?:名称|goods_name|name)\s*[:：]\s*(.+)/u', $line, $m)) $kvResult['goods_name'] = trim($m[1], '" \'');
            if (preg_match('/^(?:品牌|brand)\s*[:：]\s*(.+)/u', $line, $m)) $kvResult['brand'] = trim($m[1], '" \'');
            if (preg_match('/^(?:分类|category)\s*[:：]\s*(.+)/u', $line, $m)) $kvResult['category'] = trim($m[1], '" \'');
            if (preg_match('/^(?:规格|spec)\s*[:：]\s*(.+)/u', $line, $m)) $kvResult['spec'] = trim($m[1], '" \'');
            if (preg_match('/^(?:条形码|barcode)\s*[:：]\s*(.+)/u', $line, $m)) $kvResult['barcode'] = trim($m[1], '" \'');
            if (preg_match('/^(?:置信度|confidence)\s*[:：]?\s*([\d.]+)/u', $line, $m)) $kvResult['confidence'] = $m[1];
        }
        if (!empty($kvResult) && isset($kvResult['goods_name'])) {
            return $this->normalizeResult($kvResult);
        }

        throw new Exception('AI 返回结果无法解析: ' . $debugContent);
    }

    /**
     * 判断解析结果是否包含可识别的字段
     */
    private function hasRecognizableFields($result) {
        $keys = ['goods_name', 'name', 'category', 'barcode', 'brand'];
        foreach ($keys as $k) {
            if (isset($result[$k]) && $result[$k] !== null && $result[$k] !== '') return true;
        }
        // 也检查中文 key
        $cnKeys = ['名称', '品牌', '分类'];
        foreach ($cnKeys as $k) {
            if (isset($result[$k]) && $result[$k] !== null && $result[$k] !== '') return true;
        }
        return false;
    }

    /**
     * 标准化识别结果（兼容中英文 key，null 值转为空字符串）
     */
    private function normalizeResult($result) {
        // 辅助：安全取值，null/"null"/"无" 都视为空
        $safe = function($val) {
            if ($val === null || $val === 'null' || $val === '无' || $val === '暂无' || $val === 'N/A') return '';
            return trim((string)$val);
        };
        return [
            'barcode'      => $safe($result['barcode'] ?? $result['条形码'] ?? ''),
            'goods_name'   => $safe($result['goods_name'] ?? $result['name'] ?? $result['名称'] ?? ''),
            'brand'        => $safe($result['brand'] ?? $result['品牌'] ?? ''),
            'spec'         => $safe($result['spec'] ?? $result['规格'] ?? ''),
            'category'     => $safe($result['category'] ?? $result['分类'] ?? '') ?: '其他',
            'expire_date'  => $safe($result['expire_date'] ?? $result['expiry_date'] ?? $result['保质期'] ?? ''),
            'storage_tip'  => $safe($result['storage_tip'] ?? $result['storage_suggestion'] ?? $result['存放建议'] ?? ''),
            'confidence'   => floatval($result['confidence'] ?? $result['置信度'] ?? 0.8),
        ];
    }

    /**
     * 压缩图片：限制最大尺寸，降低质量，减少API传输大小
     * @param string $imageData 原始图片二进制数据
     * @param int $maxSize 最大边长（px）
     * @param int $quality JPEG质量（1-100）
     * @return string 压缩后的图片二进制数据
     */
    private function compressImage($imageData, $maxSize = 1600, $quality = 75) {
        // 没有 GD 扩展就直接返回原图
        if (!function_exists('imagecreatefromstring') || !function_exists('imagejpeg')) {
            return $imageData;
        }
        try {
            $img = @imagecreatefromstring($imageData);
            if (!$img) return $imageData;

            $origW = imagesx($img);
            $origH = imagesy($img);

            // 如果图片已经够小，直接返回
            if ($origW <= $maxSize && $origH <= $maxSize && strlen($imageData) < 500000) {
                imagedestroy($img);
                return $imageData;
            }

            // 计算缩放比例
            $ratio = min($maxSize / $origW, $maxSize / $origH, 1.0);
            $newW = (int)($origW * $ratio);
            $newH = (int)($origH * $ratio);

            $newImg = imagecreatetruecolor($newW, $newH);
            // 保留透明通道
            imagealphablending($newImg, false);
            imagesavealpha($newImg, true);
            imagecopyresampled($newImg, $img, 0, 0, 0, 0, $newW, $newH, $origW, $origH);

            ob_start();
            imagejpeg($newImg, null, $quality);
            $compressed = ob_get_clean();

            imagedestroy($img);
            imagedestroy($newImg);

            if ($compressed && strlen($compressed) > 0) {
                error_log('Agent: image compressed ' . $origW . 'x' . $origH . ' -> ' . $newW . 'x' . $newH . ', ' . strlen($imageData) . ' -> ' . strlen($compressed) . ' bytes');
                return $compressed;
            }
            return $imageData;
        } catch (Exception $e) {
            error_log('Agent: compressImage error: ' . $e->getMessage());
            return $imageData;
        }
    }

    /**
     * 检测图片MIME类型（不依赖finfo扩展）
     */
    private function detectMime($data, $path = '') {
        // 通过文件头魔数检测
        $header = substr($data, 0, 4);
        if (substr($header, 0, 3) === "\xFF\xD8\xFF") return 'image/jpeg';
        if (substr($header, 0, 8) === "\x89PNG\r\n\x1a\n") return 'image/png';
        if (substr($header, 0, 4) === 'GIF8') return 'image/gif';
        if (substr($header, 0, 4) === 'RIFF') return 'image/webp';
        // 通过扩展名检测
        $ext = strtolower(pathinfo($path, PATHINFO_EXTENSION));
        $map = ['jpg'=>'image/jpeg','jpeg'=>'image/jpeg','png'=>'image/png','gif'=>'image/gif','webp'=>'image/webp','bmp'=>'image/bmp'];
        return $map[$ext] ?? 'image/jpeg';
    }

    // ========== 日志方法 ==========

    private function createCallLog($imageUrl) {
        try {
            $db = getDB();
            $now = time();
            $stmt = $db->prepare("INSERT INTO ai_call_log (user_id, type, image_url, ai_provider, ai_model, created_at) VALUES (?, 'recognize', ?, ?, ?, ?)");
            $stmt->execute([$this->userId, $imageUrl, $this->config['provider'], $this->config['model'], $now]);
            return $db->lastInsertId();
        } catch (Exception $e) {
            error_log('createCallLog error: ' . $e->getMessage());
            return 0;
        }
    }

    private function updateCallLog($logId, $data) {
        try {
            if (!$logId) return;
            $db = getDB();
            $sets = [];
            $params = [];
            foreach ($data as $k => $v) {
                $sets[] = "$k = ?";
                $params[] = $v;
            }
            $params[] = $logId;
            $db->prepare("UPDATE ai_call_log SET " . implode(', ', $sets) . " WHERE id = ?")->execute($params);
        } catch (Exception $e) {
            error_log('updateCallLog error: ' . $e->getMessage());
        }
    }

    private function logToolCall($callId, $toolName, $params, $result, $duration) {
        try {
            $db = getDB();
            $status = isset($result['error']) ? 0 : 1;
            $stmt = $db->prepare("INSERT INTO ai_tool_call_log (call_id, tool_name, tool_params, tool_result, execute_time, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
            $stmt->execute([
                $callId,
                $toolName,
                json_encode($params, JSON_UNESCAPED_UNICODE),
                json_encode($result, JSON_UNESCAPED_UNICODE),
                $duration,
                $status,
                time()
            ]);
        } catch (Exception $e) {
            error_log('logToolCall error: ' . $e->getMessage());
        }
    }

    private function updateApiStats($apiId, $success) {
        try {
            $db = getDB();
            $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, last_call_time = ? WHERE id = ?")
                ->execute([time(), $apiId]);
            if ($success) {
                $db->prepare("UPDATE api_config SET success_calls = success_calls + 1 WHERE id = ?")
                    ->execute([$apiId]);
            }
        } catch (Exception $e) {
            error_log('updateApiStats error: ' . $e->getMessage());
        }
    }

    // ========== HTTP 工具 ==========

    private function httpPost($url, $body, $headers = []) {
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 60);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        if (!empty($headers)) {
            curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        }
        $response = curl_exec($ch);
        $errno = curl_errno($ch);
        $error = curl_error($ch);
        curl_close($ch);

        if ($errno) {
            throw new Exception("HTTP 请求失败[curl {$errno}]: {$error}");
        }
        return $response;
    }
}
