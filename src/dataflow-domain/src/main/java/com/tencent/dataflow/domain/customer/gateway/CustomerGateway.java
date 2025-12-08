package com.tencent.dataflow.domain.customer.gateway;

import com.tencent.dataflow.domain.customer.Customer;

public interface CustomerGateway {
    Customer getByById(String customerId);
}
