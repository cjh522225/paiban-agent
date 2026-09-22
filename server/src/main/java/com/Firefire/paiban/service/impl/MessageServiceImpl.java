package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.Message;
import com.Firefire.paiban.mapper.MessageMapper;
import com.Firefire.paiban.service.MessageService;
import org.springframework.stereotype.Service;

@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
}
