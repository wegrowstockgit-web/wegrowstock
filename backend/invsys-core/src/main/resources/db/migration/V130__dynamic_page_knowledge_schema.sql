-- Platform-wide weGrowStock Page Info ("i") knowledge. Not tenant-scoped.

CREATE TABLE page_knowledge_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_pattern VARCHAR(255) UNIQUE NOT NULL,
    category VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    role_privileges TEXT NOT NULL,
    key_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    common_mistakes JSONB NOT NULL DEFAULT '[]'::jsonb,
    pro_tip TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_page_knowledge_route ON page_knowledge_configs (route_pattern);
CREATE INDEX idx_page_knowledge_category ON page_knowledge_configs (category);

COMMENT ON TABLE page_knowledge_configs IS
    'Dynamic weGrowStock Page Info overlay content, managed from the control plane.';

GRANT SELECT ON page_knowledge_configs TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON page_knowledge_configs TO app_owner;
