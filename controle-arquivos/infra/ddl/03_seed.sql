-- Seed inicial: job de controle de concorrência
INSERT INTO job_concurrency_control (
    idt_job_schedules, des_job_name, des_status, des_description,
    flg_active, dat_creation
) VALUES (
    job_concurrency_seq.NEXTVAL,
    'ORCHESTRATOR-FILE-COLLECTOR',
    'PENDING',
    'Job responsável por coletar arquivos dos SFTPs externos',
    1,
    SYSTIMESTAMP
);

-- Exemplo de servidor SFTP externo
INSERT INTO server (
    idt_server, cod_server, cod_vault, des_vault_secret,
    des_server_type, des_server_origin, dat_creation, flg_active
) VALUES (
    server_seq.NEXTVAL, 'SFTP-EXTERNO-PAGSEGURO',
    'concil_control_arquivos', 'concil_controle_arquivo/sftp_pags',
    'SFTP', 'EXTERNO', SYSDATE, 1
);

-- Exemplo de servidor S3 destino
INSERT INTO server (
    idt_server, cod_server, cod_vault, des_vault_secret,
    des_server_type, des_server_origin, dat_creation, flg_active
) VALUES (
    server_seq.NEXTVAL, 'S3-PAGSEGURO',
    'concil_control_arquivos', 'concil_controle_arquivo/s3_pags',
    'S3', 'INTERNO', SYSDATE, 1
);

COMMIT;
