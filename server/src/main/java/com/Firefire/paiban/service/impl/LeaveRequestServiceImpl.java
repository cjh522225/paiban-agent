package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.LeaveRequest;
import com.Firefire.paiban.mapper.LeaveRequestMapper;
import com.Firefire.paiban.service.LeaveRequestService;
import org.springframework.stereotype.Service;

@Service
public class LeaveRequestServiceImpl extends ServiceImpl<LeaveRequestMapper, LeaveRequest> implements LeaveRequestService {
}
