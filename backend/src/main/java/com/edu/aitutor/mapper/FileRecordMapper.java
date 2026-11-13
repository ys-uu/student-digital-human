package com.edu.aitutor.mapper;

import com.edu.aitutor.entity.FileRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 统一文件元数据 Mapper（注解风格，无 XML）。
 */
@Mapper
public interface FileRecordMapper {

    String COLUMNS = "id, bucket_name, object_key, file_name, file_type, file_size, " +
            "file_hash, biz_type, uploader_id, create_time, is_deleted";

    /** 新增文件元数据，回填自增主键 */
    @Insert("insert into file_record (bucket_name, object_key, file_name, file_type, file_size, " +
            "file_hash, biz_type, uploader_id) values (#{bucketName}, #{objectKey}, #{fileName}, " +
            "#{fileType}, #{fileSize}, #{fileHash}, #{bizType}, #{uploaderId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileRecord record);

    /** 按主键查询未删除记录 */
    @Select("select " + COLUMNS + " from file_record where id = #{id} and is_deleted = 0")
    FileRecord selectById(@Param("id") Long id);

    /** 确认上传时回写文件哈希 */
    @Update("update file_record set file_hash = #{fileHash} where id = #{id}")
    int updateHash(@Param("id") Long id, @Param("fileHash") String fileHash);

    /**
     * 分页查询本人上传的文件，可按业务类型与文件名关键字过滤。
     * 本期仅返回本人记录；公共/课程权限在知识库阶段扩展。
     */
    @Select("<script>" +
            "select " + COLUMNS + " from file_record " +
            "where is_deleted = 0 and uploader_id = #{uploaderId} " +
            "<if test='bizType != null'>and biz_type = #{bizType} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "and file_name like concat('%', #{keyword}, '%') </if>" +
            "order by id desc limit #{offset}, #{size}" +
            "</script>")
    List<FileRecord> selectPage(@Param("uploaderId") Long uploaderId,
                                @Param("bizType") Integer bizType,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("size") int size);

    /** 分页计数，过滤条件与 selectPage 保持一致 */
    @Select("<script>" +
            "select count(*) from file_record " +
            "where is_deleted = 0 and uploader_id = #{uploaderId} " +
            "<if test='bizType != null'>and biz_type = #{bizType} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "and file_name like concat('%', #{keyword}, '%') </if>" +
            "</script>")
    long countPage(@Param("uploaderId") Long uploaderId,
                   @Param("bizType") Integer bizType,
                   @Param("keyword") String keyword);
}
