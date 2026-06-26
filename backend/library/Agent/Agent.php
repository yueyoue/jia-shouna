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
     * @param string $imageUrl 图片URL
     * @return array 结构化识别结果
     */
    public function recognize($imageUrl) {
        $startTime = microtime(true);

        // 创建调用日志
        $this->callLogId = $this->createCallLog($imageUrl);

        try {
            $systemPrompt = get_ai_system_prompt();

            $messages = [
                ['role' => 'system', 'content' => $systemPrompt],
                [
                    'role' => 'user',
                    'content' => [
                        ['type' => 'image_url', 'image_url' => ['url' => $imageUrl]],
                        ['type' => 'text', 'text' => '请识别这张图片中的物品信息，优先解析条码。'],
                    ]
                ]
            ];

            $payload = [
                'model' => $this->config['model'],
                'messages' => $messages,
                'tools' => $this->tools,
                'tool_choice' => 'auto',
                'temperature' => 0.2,
            ];

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
            $content = $response['choices'][0]['message']['content'] ?? '';

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
        // 尝试直接解析
        $result = json_decode($content, true);
        if ($result && is_array($result)) {
            return $this->normalizeResult($result);
        }

        // 尝试从 markdown 代码块中提取
        if (preg_match('/```(?:json)?\s*\n?(.*?)\n?```/s', $content, $m)) {
            $result = json_decode($m[1], true);
            if ($result && is_array($result)) {
                return $this->normalizeResult($result);
            }
        }

        // 尝试找第一个 { 到最后一个 }
        $start = strpos($content, '{');
        $end = strrpos($content, '}');
        if ($start !== false && $end !== false && $end > $start) {
            $result = json_decode(substr($content, $start, $end - $start + 1), true);
            if ($result && is_array($result)) {
                return $this->normalizeResult($result);
            }
        }

        throw new Exception('AI 返回结果无法解析');
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
