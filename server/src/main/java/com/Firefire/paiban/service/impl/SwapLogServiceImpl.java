package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.SwapLog;
import com.Firefire.paiban.mapper.SwapLogMapper;
import com.Firefire.paiban.service.SwapLogService;
import org.springframework.stereotype.Service;

@Service
public class SwapLogServiceImpl extends ServiceImpl<SwapLogMapper, SwapLog> implements SwapLogService {
}
