alter table event_disciplines
    add column entry_fee_amount numeric(12, 2) not null default 0,
    add column entry_fee_currency varchar(3) not null default 'RUB';

alter table event_disciplines
    add constraint chk_event_disciplines_entry_fee_amount
        check (entry_fee_amount >= 0);

alter table event_disciplines
    add constraint chk_event_disciplines_entry_fee_currency
        check (
            char_length(entry_fee_currency) = 3
            and entry_fee_currency = upper(entry_fee_currency)
        );