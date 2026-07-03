package com.auto.ecommerce.ecommerceauto.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sop_template")
public class SopTemplateEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 数据库字段为 gmt_create_time，MyBatis-Plus 会按下划线规则映射。 */
    private Date gmtCreateTime;
    /** 列表按这个字段展示最近修改的模板。 */
    private Date gmtModifiedTime;
    private String name;
    private String titlePrompt;
    private String mainImagePrompt;
}
