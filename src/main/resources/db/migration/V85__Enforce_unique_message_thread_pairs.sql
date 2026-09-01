WITH ranked_message_threads AS (
    SELECT
        id,
        FIRST_VALUE(id) OVER thread_pair_window AS keep_id,
        ROW_NUMBER() OVER thread_pair_window AS duplicate_rank
    FROM public.message_threads
    WINDOW thread_pair_window AS (
        PARTITION BY LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id)
        ORDER BY last_message_at DESC NULLS LAST, created_at DESC NULLS LAST, id ASC
    )
)
UPDATE public.messages
SET conversation_id = ranked_message_threads.keep_id
FROM ranked_message_threads
WHERE ranked_message_threads.duplicate_rank > 1
  AND public.messages.conversation_id = ranked_message_threads.id;

WITH ranked_message_threads AS (
    SELECT
        id,
        ROW_NUMBER() OVER thread_pair_window AS duplicate_rank
    FROM public.message_threads
    WINDOW thread_pair_window AS (
        PARTITION BY LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id)
        ORDER BY last_message_at DESC NULLS LAST, created_at DESC NULLS LAST, id ASC
    )
)
DELETE FROM public.message_threads
USING ranked_message_threads
WHERE ranked_message_threads.duplicate_rank > 1
  AND public.message_threads.id = ranked_message_threads.id;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_message_threads_ordered_participants
    ON public.message_threads (LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id));
