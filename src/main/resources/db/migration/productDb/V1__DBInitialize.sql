-- Check and drop foreign key before deleting the table
set @fkExists = (select 1 from information_schema.table_constraints where table_schema = DATABASE() and table_name = 'products' and constraint_name = 'FK_Product_Category' and constraint_type = 'FOREIGN KEY');
set @DropFkQuery = if (@fkExists > 0, 'alter table products drop foreign key FK_Product_Category', 'select ''Foreign key does not exist''');
PREPARE stmt FROM @DropFkQuery;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

drop table if exists categories;

drop table if exists categories_seq;

drop table if exists products;

drop table if exists products_seq;

create table categories (
                            status tinyint check (status between 0 and 2),
                            created_on datetime(6),
                            id bigint not null,
                            modified_on datetime(6),
                            created_by varchar(255),
                            description varchar(255),
                            image_url varchar(255),
                            modified_by varchar(255),
                            name varchar(255),
                            primary key (id)) engine=InnoDB;

create table categories_seq (next_val bigint) engine=InnoDB;

insert into categories_seq values ( 1 );

create table products (
                          price float(53),
                          status tinyint check (status between 0 and 1),
                          category_id bigint not null,
                          created_on datetime(6),
                          id bigint not null,
                          modified_on datetime(6),
                          created_by varchar(255),
                          description varchar(255),
                          image_url varchar(255),
                          modified_by varchar(255),
                          name varchar(255),
                          primary key (id)) engine=InnoDB;

create table products_seq (next_val bigint) engine=InnoDB;
insert into products_seq values ( 1 );

alter table products add constraint FK_Product_Category foreign key (category_id) references categories (id);

