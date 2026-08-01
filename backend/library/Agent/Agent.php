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
     * @return array 结构化识别结果
     */
    public function recognize($imageUrl) {
        $startTime = microtime(true);

        // 创建调用日志
        $this->callLogId = $this->createCallLog($imageUrl);

        try {
            $systemPrompt = get_ai_system_prompt();

            // 将图片转为 base64 data URL（确保AI模型能访问）
            $imageDataUrl = $imageUrl;
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
                    $finfo = new finfo(FILEINFO_MIME_TYPE);
                    $mime = $finfo->buffer($imgData) ?: 'image/jpeg';
                    $imageDataUrl = 'data:' . $mime . ';base64,' . base64_encode($imgData);
                }
            } elseif (file_exists($imageUrl)) {
                // 是本地文件路径
                $imgData = file_get_contents($imageUrl);
                if ($imgData) {
                    $finfo = new finfo(FILEINFO_MIME_TYPE);
                    $mime = $finfo->buffer($imgData) ?: 'image/jpeg';
                    $imageDataUrl = 'data:' . $mime . ';base64,' . base64_encode($imgData);
                }
            }

            $messages = [
                ['role' => 'system', 'content' => $systemPrompt],
                [
                    'role' => 'user',
                    'content' => [
                        ['type' => 'image_url', 'image_url' => ['url' => $imageDataUrl]],
                        ['type' => 'text', 'text' => '请识别这张图片中的物品信息，优先解析条码。'],
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
        if ($result && is_array($result) && (isset($result['goods_name']) || isset($result['name']) || isset($result['category']))) {
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
                if ($result && is_array($result) && (isset($result['goods_name']) || isset($result['name']) || isset($result['category']))) {
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

        // 方法5: 提取 key-value 对重建
        if (preg_match('/"goods_name"\s*:\s*"([^"]+)"/', $content, $nm)) {
            $rebuild = ['goods_name' => $nm[1]];
            if (preg_match('/"brand"\s*:\s*"([^"]*)"/', $content, $b)) $rebuild['brand'] = $b[1];
            if (preg_match('/"category"\s*:\s*"([^"]*)"/', $content, $c)) $rebuild['category'] = $c[1];
            if (preg_match('/"spec"\s*:\s*"([^"]*)"/', $content, $s)) $rebuild['spec'] = $s[1];
            if (preg_match('/"expire_date"\s*:\s*"([^"]*)"/', $content, $e)) $rebuild['expire_date'] = $e[1];
            if (preg_match('/"storage_tip"\s*:\s*"([^"]*)"/', $content, $t)) $rebuild['storage_tip'] = $t[1];
            if (preg_match('/"confidence"\s*:?\s*([\d.]+)/', $content, $cf)) $rebuild['confidence'] = $cf[1];
            if (preg_match('/"barcode"\s*:\s*"([^"]*)"/', $content, $bc)) $rebuild['barcode'] = $bc[1];
            return $this->normalizeResult($rebuild);
        }

        throw new Exception('AI 返回结果无法解析: ' . $debugContent);
    }

    /**
     * 标准化识别结果
     */
    private function normalizeResult($result) {
        return [
            'barcode'      => trim($result['barcode'] ?? ''),
            'goods_name'   => trim($result['goods_name'] ?? $result['name'] ?? ''),
            'brand'        => trim($result['brand'] ?? ''),
            'spec'         => trim($result['spec'] ?? ''),
            'category'     => trim($result['category'] ?? '其他'),
            'expire_date'  => trim($result['expire_date'] ?? $result['expiry_date'] ?? ''),
            'storage_tip'  => trim($result['storage_tip'] ?? $result['storage_suggestion'] ?? ''),
            'confidence'   => floatval($result['confidence'] ?? 0.8),
        ];
    }

    // ========== 日志方法 ==========

    private function createCallLog($imageUrl) {
        $db = getDB();
        $now = time();
        $stmt = $db->prepare("INSERT INTO ai_call_log (user_id, type, image_url, ai_provider, ai_model, created_at) VALUES (?, 'recognize', ?, ?, ?, ?)");
        $stmt->execute([$this->userId, $imageUrl, $this->config['provider'], $this->config['model'], $now]);
        return $db->lastInsertId();
    }

    private function updateCallLog($logId, $data) {
        $db = getDB();
        $sets = [];
        $params = [];
        foreach ($data as $k => $v) {
            $sets[] = "$k = ?";
            $params[] = $v;
        }
        $params[] = $logId;
        $db->prepare("UPDATE ai_call_log SET " . implode(', ', $sets) . " WHERE id = ?")->execute($params);
    }

    private function logToolCall($callId, $toolName, $params, $result, $duration) {
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
    }

    private function updateApiStats($apiId, $success) {
        $db = getDB();
        $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, last_call_time = ? WHERE id = ?")
            ->execute([time(), $apiId]);
        if ($success) {
            $db->prepare("UPDATE api_config SET success_calls = success_calls + 1 WHERE id = ?")
                ->execute([$apiId]);
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
