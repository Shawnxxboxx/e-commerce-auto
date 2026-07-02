package com.auto.ecommerce.ecommerceauto.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sop_template")
public class SopTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateId;
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
