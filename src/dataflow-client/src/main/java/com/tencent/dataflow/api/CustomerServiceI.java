package com.tencent.dataflow.api;

import com.alibaba.cola.dto.MultiResponse;
import com.alibaba.cola.dto.Response;
import com.tencent.dataflow.dto.CustomerAddCmd;
import com.tencent.dataflow.dto.CustomerListByNameQry;
import com.tencent.dataflow.dto.data.CustomerDTO;

public interface CustomerServiceI {

    Response addCustomer(CustomerAddCmd customerAddCmd);

    MultiResponse<CustomerDTO> listByName(CustomerListByNameQry customerListByNameQry);
}
