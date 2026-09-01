ALTER TABLE public.message_threads ENABLE ROW LEVEL SECURITY;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.message_threads TO authenticated;

DROP POLICY IF EXISTS "Users can view their own message threads" ON public.message_threads;
DROP POLICY IF EXISTS "Users can create message threads" ON public.message_threads;
DROP POLICY IF EXISTS "Users can update their own message threads" ON public.message_threads;
DROP POLICY IF EXISTS "Users can delete their own message threads" ON public.message_threads;

CREATE POLICY "Users can view their own message threads"
    ON public.message_threads
    FOR SELECT
    TO authenticated
    USING (
        auth.uid() = user1_id OR
        auth.uid() = user2_id
    );

CREATE POLICY "Users can create message threads"
    ON public.message_threads
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = user1_id OR
        auth.uid() = user2_id
    );

CREATE POLICY "Users can update their own message threads"
    ON public.message_threads
    FOR UPDATE
    TO authenticated
    USING (
        auth.uid() = user1_id OR
        auth.uid() = user2_id
    )
    WITH CHECK (
        auth.uid() = user1_id OR
        auth.uid() = user2_id
    );

CREATE POLICY "Users can delete their own message threads"
    ON public.message_threads
    FOR DELETE
    TO authenticated
    USING (
        auth.uid() = user1_id OR
        auth.uid() = user2_id
    );
