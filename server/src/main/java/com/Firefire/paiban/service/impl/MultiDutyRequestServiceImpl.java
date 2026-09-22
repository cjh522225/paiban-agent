package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.MultiDutyRequest;
import com.Firefire.paiban.mapper.MultiDutyRequestMapper;
import com.Firefire.paiban.service.MultiDutyRequestService;
import org.springframework.stereotype.Service;

@Service
public class MultiDutyRequestServiceImpl extends ServiceImpl<MultiDutyRequestMapper, MultiDutyRequest> implements MultiDutyRequestService {
}
