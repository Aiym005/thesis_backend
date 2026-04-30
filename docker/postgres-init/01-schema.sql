CREATE TABLE IF NOT EXISTS department (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    programs JSONB,
    admin TEXT
);

CREATE TABLE IF NOT EXISTS student (
    id BIGSERIAL PRIMARY KEY,
    dep_id BIGINT REFERENCES department(id),
    firstname TEXT NOT NULL,
    lastname TEXT NOT NULL,
    mail TEXT,
    program TEXT,
    sisi_id TEXT,
    is_choosed BOOLEAN DEFAULT FALSE,
    proposed_number INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS teacher (
    id BIGSERIAL PRIMARY KEY,
    dep_id BIGINT REFERENCES department(id),
    firstname TEXT NOT NULL,
    lastname TEXT NOT NULL,
    mail TEXT,
    num_of_choosed_stud INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS topic (
    id BIGSERIAL PRIMARY KEY,
    created_at DATE NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_by_type TEXT NOT NULL,
    fields JSONB NOT NULL,
    form_id BIGINT,
    program TEXT,
    status TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS topic_request (
    id BIGSERIAL PRIMARY KEY,
    is_selected BOOLEAN DEFAULT FALSE,
    req_note TEXT,
    req_text TEXT,
    requested_by_id BIGINT NOT NULL,
    requested_by_type TEXT NOT NULL,
    selected_at DATE,
    topic_id BIGINT NOT NULL REFERENCES topic(id)
);

CREATE TABLE IF NOT EXISTS plan (
    id BIGSERIAL PRIMARY KEY,
    created_at DATE NOT NULL,
    status TEXT NOT NULL,
    student_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES topic(id)
);

CREATE TABLE IF NOT EXISTS plan_week (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
    result JSONB,
    task TEXT NOT NULL,
    week_number INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_response (
    id BIGSERIAL PRIMARY KEY,
    approver_id BIGINT NOT NULL,
    approver_type TEXT NOT NULL,
    note TEXT,
    plan_id BIGINT NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
    res TEXT NOT NULL,
    res_date DATE
);

CREATE TABLE IF NOT EXISTS workflow_review (
    id BIGINT PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
    week_number INTEGER NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewer_name TEXT NOT NULL,
    score INTEGER NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_notification (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS workflow_audit (
    id BIGINT PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id BIGINT NOT NULL,
    action TEXT NOT NULL,
    actor_name TEXT NOT NULL,
    detail TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE SEQUENCE IF NOT EXISTS auth_account_user_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS auth_account (
    user_id BIGINT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'student',
    display_name TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
