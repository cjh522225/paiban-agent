package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.MessageRead;
import com.Firefire.paiban.mapper.MessageReadMapper;
import com.Firefire.paiban.service.MessageReadService;
import org.springframework.stereotype.Service;

@Service
public class MessageReadServiceImpl extends ServiceImpl<MessageReadMapper, MessageRead> implements MessageReadService {
}
