-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
-- 实体钥匙与领还事件，非商业学习使用。

create table physical_key (
 id bigint auto_increment primary key,
 code varchar(60) not null,
 name varchar(160) not null,
 department_id bigint not null,
 category varchar(60) not null,
 cabinet varchar(120) not null,
 state varchar(30) not null,
 current_loan_id bigint,
 version bigint not null,
 note varchar(500) not null,
 unique(code),
 foreign key(department_id) references department(id)
);

create table key_grant (
 id bigint auto_increment primary key,
 key_id bigint not null,
 borrower_id bigint not null,
 created_by bigint not null,
 department_id bigint not null,
 valid_from timestamp(6) not null,
 valid_until timestamp(6) not null,
 enabled boolean not null,
 version bigint not null,
 reason varchar(500) not null,
 foreign key(key_id) references physical_key(id),
 foreign key(borrower_id) references account(id),
 foreign key(created_by) references account(id),
 foreign key(department_id) references department(id),
 check(valid_until > valid_from)
);

create table key_loan (
 id bigint auto_increment primary key,
 key_id bigint not null,
 grant_id bigint not null,
 department_id bigint not null,
 borrower_id bigint not null,
 approver_id bigint,
 issuer_id bigint,
 receiver_id bigint,
 loss_reporter_id bigint,
 state varchar(30) not null,
 purpose varchar(500) not null,
 due_at timestamp(6) not null,
 created_at timestamp(6) not null,
 approved_until timestamp(6),
 issued_at timestamp(6),
 accepted_at timestamp(6),
 returned_at timestamp(6),
 inspection varchar(30) not null,
 version bigint not null,
 foreign key(key_id) references physical_key(id),
 foreign key(grant_id) references key_grant(id),
 foreign key(department_id) references department(id),
 foreign key(borrower_id) references account(id),
 foreign key(approver_id) references account(id),
 foreign key(issuer_id) references account(id),
 foreign key(receiver_id) references account(id),
 foreign key(loss_reporter_id) references account(id)
);

create table loan_event (
 id bigint auto_increment primary key,
 loan_id bigint not null,
 actor_id bigint not null,
 action varchar(40) not null,
 note varchar(1000) not null,
 created_at timestamp(6) not null,
 foreign key(loan_id) references key_loan(id),
 foreign key(actor_id) references account(id)
);

create table key_review (
 id bigint auto_increment primary key,
 key_id bigint not null,
 requested_by bigint not null,
 reviewed_by bigint,
 state varchar(30) not null,
 reason varchar(1000) not null,
 decision varchar(1000) not null,
 created_at timestamp(6) not null,
 version bigint not null,
 foreign key(key_id) references physical_key(id),
 foreign key(requested_by) references account(id),
 foreign key(reviewed_by) references account(id)
);

alter table physical_key add constraint fk_key_loan foreign key(current_loan_id) references key_loan(id);

create index idx_loan_key_state on key_loan(key_id,state);

create index idx_loan_department on key_loan(department_id,borrower_id);

create index idx_grant_borrower on key_grant(borrower_id,key_id);

create index idx_event_loan on loan_event(loan_id,id);

create index idx_review_key on key_review(key_id,state);
