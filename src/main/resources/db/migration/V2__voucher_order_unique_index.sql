ALTER TABLE `tb_voucher_order`
    ADD UNIQUE INDEX `uk_user_voucher` (`user_id`, `voucher_id`);
