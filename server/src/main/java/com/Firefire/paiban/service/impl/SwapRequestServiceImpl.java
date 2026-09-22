package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.SwapRequest;
import com.Firefire.paiban.mapper.SwapRequestMapper;
import com.Firefire.paiban.service.SwapRequestService;
import org.springframework.stereotype.Service;

@Service
public class SwapRequestServiceImpl extends ServiceImpl<SwapRequestMapper, SwapRequest> implements SwapRequestService {
}
