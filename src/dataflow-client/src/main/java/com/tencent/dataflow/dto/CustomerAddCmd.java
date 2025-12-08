package com.tencent.dataflow.dto;

import com.tencent.dataflow.dto.data.CustomerDTO;
import lombok.Data;

@Data
public class CustomerAddCmd{

    private CustomerDTO customerDTO;

}
