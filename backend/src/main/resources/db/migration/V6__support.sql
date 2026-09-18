CREATE TABLE support_tickets (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id              BIGINT       NOT NULL,
    subject                  VARCHAR(150) NOT NULL,
    status                   ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    related_transaction_id   BIGINT       NULL,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ticket_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_ticket_ft FOREIGN KEY (related_transaction_id) REFERENCES financial_transactions(id)
) ENGINE=InnoDB;

CREATE TABLE support_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id   BIGINT        NOT NULL,
    sender_type ENUM('CUSTOMER','ADMIN') NOT NULL,
    sender_id   BIGINT        NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_msg_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets(id)
) ENGINE=InnoDB;

CREATE INDEX idx_ticket_customer ON support_tickets(customer_id, status);
CREATE INDEX idx_msg_ticket ON support_messages(ticket_id, created_at);
