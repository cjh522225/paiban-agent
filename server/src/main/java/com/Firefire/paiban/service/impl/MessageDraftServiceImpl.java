package com.Firefire.paiban.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Firefire.paiban.entity.MessageDraft;
import com.Firefire.paiban.mapper.MessageDraftMapper;
import com.Firefire.paiban.service.MessageDraftService;
import org.springframework.stereotype.Service;

@Service
public class MessageDraftServiceImpl extends ServiceImpl<MessageDraftMapper, MessageDraft> implements MessageDraftService {
}
