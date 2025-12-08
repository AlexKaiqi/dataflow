package com.tencent.dataflow.domain.customer.gateway;

import com.tencent.dataflow.domain.customer.Credit;

//Assume that the credit info is in another distributed Service
public interface CreditGateway {
    Credit getCredit(String customerId);
}
