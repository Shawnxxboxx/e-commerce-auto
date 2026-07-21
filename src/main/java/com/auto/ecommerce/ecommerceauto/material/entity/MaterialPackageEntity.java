package com.auto.ecommerce.ecommerceauto.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_package")
public class MaterialPackageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialPackageId;
    private String originalDirectoryName;
    private String storagePath;
    private String parsedJson;
    private Integer fileCount;
    private Long totalSize;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
