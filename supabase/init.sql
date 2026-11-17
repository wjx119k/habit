-- ============================================================
--  Habit · Supabase 数据库初始化脚本
--  用法：Supabase 控制台 → 左侧 SQL Editor → New query →
--        把本文件内容整段粘贴进去 → 点 Run
--  可重复执行（已存在的对象不会报错）
-- ============================================================


-- ------------------------------------------------------------
-- 1. 云同步用的数据表（用户整份数据存一行 JSON）
-- ------------------------------------------------------------
create table if not exists public.user_data (
    user_id    uuid primary key references auth.users on delete cascade,
    payload    jsonb not null,
    updated_at timestamptz not null default now()
);

alter table public.user_data enable row level security;

drop policy if exists "只能读写自己的数据" on public.user_data;
create policy "只能读写自己的数据"
    on public.user_data
    for all
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);


-- ------------------------------------------------------------
-- 2. 应用内反馈（登录用户在 App 里直接提交，不需要邮箱）
-- ------------------------------------------------------------
create table if not exists public.feedback (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid references auth.users on delete cascade,
    message     text not null,
    app_version text,
    device      text,
    created_at  timestamptz not null default now()
);

alter table public.feedback enable row level security;

-- 登录用户可以提交，且 user_id 必须等于自己的 uid
drop policy if exists "登录用户可以提交反馈" on public.feedback;
create policy "登录用户可以提交反馈"
    on public.feedback
    for insert
    to authenticated
    with check (auth.uid() = user_id);

-- 用户只能看到自己提交过的（作者在控制台用 service_role 看全部）
drop policy if exists "只能看自己的反馈" on public.feedback;
create policy "只能看自己的反馈"
    on public.feedback
    for select
    to authenticated
    using (auth.uid() = user_id);


-- ------------------------------------------------------------
-- 3. 索引（按时间倒序查看反馈会快一点）
-- ------------------------------------------------------------
create index if not exists feedback_created_at_idx
    on public.feedback (created_at desc);


-- ------------------------------------------------------------
-- 4. 自检：执行完应该能看到两张表
-- ------------------------------------------------------------
-- select table_name from information_schema.tables
--  where table_schema = 'public' order by table_name;
