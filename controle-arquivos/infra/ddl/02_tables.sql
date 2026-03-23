-- ============================================================
-- job_concurrency_control
-- ============================================================
CREATE TABLE job_concurrency_control (
    idt_job_schedules NUMBER(19)    NOT NULL,
    des_job_name      VARCHAR2(100) NOT NULL,
    des_status        VARCHAR2(100) NOT NULL,
    des_description   VARCHAR2(255),
    dat_last_execution TIMESTAMP,
    flg_active        NUMBER(1)     NOT NULL,
    dat_creation      TIMESTAMP     NOT NULL,
    dat_update        TIMESTAMP,
    idt_job_execution VARCHAR2(100),
    idt_datacenter    VARCHAR2(100),
    CONSTRAINT pk_job_concurrency PRIMARY KEY (idt_job_schedules)
);

-- ============================================================
-- server
-- ============================================================
CREATE TABLE server (
    idt_server        NUMBER(19)    NOT NULL,
    cod_server        VARCHAR2(100) NOT NULL,
    cod_vault         VARCHAR2(100) NOT NULL,
    des_vault_secret  VARCHAR2(255) NOT NULL,
    des_server_type   VARCHAR2(50)  NOT NULL,
    des_server_origin VARCHAR2(50)  NOT NULL,
    dat_creation      DATE          NOT NULL,
    dat_update        DATE,
    nam_change_agent  VARCHAR2(50),
    flg_active        NUMBER(1)     NOT NULL,
    CONSTRAINT pk_server PRIMARY KEY (idt_server),
    CONSTRAINT server_idx_01 UNIQUE (cod_server, flg_active)
);

-- ============================================================
-- sever_paths
-- ============================================================
CREATE TABLE sever_paths (
    idt_sever_path   NUMBER(19)    NOT NULL,
    idt_server       NUMBER(19)    NOT NULL,
    idt_acquirer     NUMBER(19)    NOT NULL,
    des_path         VARCHAR2(255) NOT NULL,
    des_path_type    VARCHAR2(50)  NOT NULL,
    dat_creation     DATE          NOT NULL,
    dat_update       DATE,
    nam_change_agent VARCHAR2(50),
    flg_active       NUMBER(1)     NOT NULL,
    CONSTRAINT pk_sever_paths PRIMARY KEY (idt_sever_path),
    CONSTRAINT fk_sever_paths_server FOREIGN KEY (idt_server) REFERENCES server(idt_server)
);

-- ============================================================
-- sever_paths_in_out
-- ============================================================
CREATE TABLE sever_paths_in_out (
    idt_sever_paths_in_out NUMBER(19)   NOT NULL,
    idt_sever_path_origin  NUMBER(19)   NOT NULL,
    idt_sever_destination  NUMBER(19)   NOT NULL,
    des_link_type          VARCHAR2(50) NOT NULL,
    dat_creation           DATE         NOT NULL,
    dat_update             DATE,
    nam_change_agent       VARCHAR2(50),
    flg_active             NUMBER(1)    NOT NULL,
    CONSTRAINT pk_sever_paths_in_out PRIMARY KEY (idt_sever_paths_in_out),
    CONSTRAINT sever_paths_in_out_idx_01 UNIQUE (idt_sever_path_origin, idt_sever_destination, des_link_type, flg_active),
    CONSTRAINT fk_spio_origin FOREIGN KEY (idt_sever_path_origin) REFERENCES sever_paths(idt_sever_path),
    CONSTRAINT fk_spio_dest   FOREIGN KEY (idt_sever_destination)  REFERENCES sever_paths(idt_sever_path)
);

-- ============================================================
-- layout
-- ============================================================
CREATE TABLE layout (
    idt_layout            NUMBER(19)    NOT NULL,
    cod_layout            VARCHAR2(100) NOT NULL,
    idt_acquirer          NUMBER(19)    NOT NULL,
    des_version           VARCHAR2(30),
    des_file_type         VARCHAR2(10)  NOT NULL,
    des_transaction_type  VARCHAR2(100) NOT NULL,
    des_distribution_type VARCHAR2(100) NOT NULL,
    dat_creation          DATE          NOT NULL,
    dat_update            DATE,
    nam_change_agent      VARCHAR2(50)  NOT NULL,
    flg_active            NUMBER(1)     NOT NULL,
    CONSTRAINT pk_layout PRIMARY KEY (idt_layout)
);

-- ============================================================
-- layout_identification_rule
-- ============================================================
CREATE TABLE layout_identification_rule (
    idt_rule               NUMBER(19)    NOT NULL,
    idt_layout             NUMBER(19)    NOT NULL,
    des_rule               VARCHAR2(255) NOT NULL,
    des_value_origin       VARCHAR2(50)  NOT NULL,
    des_criterion_type_enum VARCHAR2(50) NOT NULL,
    num_starting_position  NUMBER(5),
    num_ending_position    NUMBER(5),
    des_value              VARCHAR2(255),
    des_tag                VARCHAR2(255),
    des_key                VARCHAR2(255),
    dat_creation           DATE          NOT NULL,
    dat_update             DATE,
    nam_change_agent       VARCHAR2(50)  NOT NULL,
    flg_active             NUMBER(1),
    CONSTRAINT pk_layout_id_rule PRIMARY KEY (idt_rule),
    CONSTRAINT fk_layout_id_rule FOREIGN KEY (idt_layout) REFERENCES layout(idt_layout)
);

-- ============================================================
-- customer_identification
-- ============================================================
CREATE TABLE customer_identification (
    idt_customer_identification NUMBER(19) NOT NULL,
    idt_client                  NUMBER(19) NOT NULL,
    idt_acquirer                NUMBER(19) NOT NULL,
    idt_merchant                NUMBER(19),
    dat_start                   DATE,
    dat_end                     DATE,
    idt_plan                    NUMBER(19),
    flg_is_prioritary           NUMBER(1),
    num_processing_weight       NUMBER(5),
    dat_creation                DATE       NOT NULL,
    dat_update                  DATE,
    nam_change_agent            VARCHAR2(50),
    flg_active                  NUMBER(1)  NOT NULL,
    CONSTRAINT pk_customer_identification PRIMARY KEY (idt_customer_identification),
    CONSTRAINT customer_identification_idx_01 UNIQUE (idt_client, idt_acquirer, idt_merchant, flg_active)
);

-- ============================================================
-- customer_identification_rule
-- ============================================================
CREATE TABLE customer_identification_rule (
    idt_rule                    NUMBER(19)    NOT NULL,
    idt_customer_identification NUMBER(19)    NOT NULL,
    des_rule                    VARCHAR2(255) NOT NULL,
    des_criterion_type_enum     VARCHAR2(50)  NOT NULL,
    num_starting_position       NUMBER(5),
    num_ending_position         NUMBER(5),
    des_value                   VARCHAR2(255) NOT NULL,
    dat_creation                DATE          NOT NULL,
    dat_update                  DATE,
    nam_change_agent            VARCHAR2(50)  NOT NULL,
    flg_active                  NUMBER(1),
    CONSTRAINT pk_customer_id_rule PRIMARY KEY (idt_rule),
    CONSTRAINT fk_customer_id_rule FOREIGN KEY (idt_customer_identification)
        REFERENCES customer_identification(idt_customer_identification)
);

-- ============================================================
-- file_origin
-- ============================================================
CREATE TABLE file_origin (
    idt_file_origin        NUMBER(19)    NOT NULL,
    idt_acquirer           NUMBER(19),
    idt_layout             NUMBER(19),
    des_file_name          VARCHAR2(255) NOT NULL,
    num_file_size          NUMBER(19),
    des_file_mime_type     VARCHAR2(100),
    des_file_type          VARCHAR2(10),
    des_transaction_type   VARCHAR2(100) NOT NULL,
    dat_timestamp_file     TIMESTAMP     NOT NULL,
    idt_sever_paths_in_out NUMBER(19)    NOT NULL,
    dat_creation           DATE          NOT NULL,
    dat_update             DATE,
    nam_change_agent       VARCHAR2(50),
    flg_active             NUMBER(1)     NOT NULL,
    CONSTRAINT pk_file_origin PRIMARY KEY (idt_file_origin),
    CONSTRAINT file_origin_idx_01 UNIQUE (des_file_name, idt_acquirer, dat_timestamp_file, flg_active),
    CONSTRAINT fk_file_origin_layout FOREIGN KEY (idt_layout) REFERENCES layout(idt_layout),
    CONSTRAINT fk_file_origin_spio   FOREIGN KEY (idt_sever_paths_in_out) REFERENCES sever_paths_in_out(idt_sever_paths_in_out)
);

-- ============================================================
-- file_origin_client
-- ============================================================
CREATE TABLE file_origin_client (
    idt_file_origin_client NUMBER(19) NOT NULL,
    idt_file_origin        NUMBER(19) NOT NULL,
    idt_client             NUMBER(19),
    dat_creation           DATE       NOT NULL,
    dat_update             DATE,
    nam_change_agent       VARCHAR2(50),
    flg_active             NUMBER(1)  NOT NULL,
    CONSTRAINT pk_file_origin_client PRIMARY KEY (idt_file_origin_client),
    CONSTRAINT fk_foc_file_origin FOREIGN KEY (idt_file_origin) REFERENCES file_origin(idt_file_origin)
);

-- ============================================================
-- file_origin_client_processing
-- ============================================================
CREATE TABLE file_origin_client_processing (
    idt_file_origin_processing NUMBER(19)    NOT NULL,
    idt_file_origin_client     NUMBER(19)    NOT NULL,
    des_step                   VARCHAR2(50)  NOT NULL,
    des_status                 VARCHAR2(50),
    des_message_error          VARCHAR2(4000),
    des_message_alert          VARCHAR2(4000),
    dat_step_start             DATE,
    dat_step_end               DATE,
    jsn_additional_info        VARCHAR2(4000),
    dat_creation               DATE          NOT NULL,
    dat_update                 DATE,
    nam_change_agent           VARCHAR2(50),
    flg_active                 NUMBER(1)     NOT NULL,
    CONSTRAINT pk_file_origin_proc PRIMARY KEY (idt_file_origin_processing),
    CONSTRAINT fk_focp_client FOREIGN KEY (idt_file_origin_client)
        REFERENCES file_origin_client(idt_file_origin_client)
);
