-- KEYS: Список бизнес-ключей идемпотентности (например, email:dedup:welcome:user_1)
-- ARGV[1...N]: Список Lease-строк (формат "topic:partition@offset|instance_id")
-- ARGV[N+1]: TTL блокировки в секундах (например, 90)
-- ARGV[N+2]: Константа терминального статуса (например, "SENT")

local n = #KEYS
local ttl = ARGV[n + 1]
local sent_status = ARGV[n + 2]
local results = {}

for i = 1, n do
    local key = KEYS[i]
    local my_lease = ARGV[i]
    local current_val = redis.call('GET', key)

    if not current_val then
        -- СЛУЧАЙ 1: Ключа нет. Чистый захват.
        redis.call('SET', key, my_lease, 'EX', ttl)
        results[i] = 'ACQUIRED'

    elseif current_val == sent_status then
        -- СЛУЧАЙ 2: Письмо уже было успешно отправлено ранее.
        results[i] = 'SENT'

    elseif current_val == my_lease then
        -- СЛУЧАЙ 3: САМОЗАХВАТ (Self-Fencing).
        -- Текущий воркер уже владеет этим локом для этого самого сообщения.
        -- Это происходит при ретрае батча (например, из-за сбоя сети на этапе финализации).
        -- Обновляем TTL, чтобы лок не протух в процессе повторной попытки.
        redis.call('EXPIRE', key, ttl)
        results[i] = 'ACQUIRED'

    else
        -- СЛУЧАЙ 4: КОНФЛИКТ (Fencing Action).
        -- Либо лок держит другой инстанс (другой instance_id),
        -- либо тот же инстанс обрабатывает ДРУГОЕ сообщение для того же бизнес-ключа (другой offset).
        results[i] = 'PROCESSING'
    end
end

return results