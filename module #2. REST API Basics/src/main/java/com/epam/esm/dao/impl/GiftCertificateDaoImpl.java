package com.epam.esm.dao.impl;

import com.epam.esm.dao.GiftCertificateDao;
import com.epam.esm.dao.TagDao;
import com.epam.esm.entity.GiftCertificate;
import com.epam.esm.dao.mapper.GiftCertificateMapper;
import static com.epam.esm.dao.column.GiftCertificateTableConst.*;

import com.epam.esm.entity.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

@Repository
public class GiftCertificateDaoImpl implements GiftCertificateDao {
    private static final GiftCertificateMapper giftMapper = new GiftCertificateMapper();    //in springConfig
    private static final String SELECT_ALL = "SELECT id, name, description, price, create_date, last_update_date, " +
            "duration_days FROM giftcertificate";
    private static final String SELECT_BY_ID = "SELECT id, name, description, price, create_date, last_update_date, " +
            "duration_days FROM giftcertificate WHERE id=?";
    private static final String SELECT_BY_NAME = "SELECT id, name, description, price, create_date, last_update_date, " +
            "duration_days FROM giftcertificate WHERE name=?";
    private static final String SELECT_BY_DESCRIPTION = "SELECT id, name, description, price, create_date, " +
            "last_update_date, duration_days FROM giftcertificate WHERE description=?";
    private static final String UPDATE = "UPDATE giftcertificate SET name = ?, description = ?, price = ?, " +
            "last_update_date = ?, duration_days = ? WHERE id = ?";
    private static final String DELETE_BY_ID = "DELETE FROM giftcertificate WHERE id = ?";
    private static final String TAG_CERT_INSERT = "INSERT INTO tag_certificate (tag_id, certificate_id) VALUES (?, ?)";
    private static final String TAG_CERT_SELECT_BY_TAG_ID = "SELECT certificate_id FROM tag_certificate WHERE tag_id = ?";
    private static final String TAG_CERT_SELECT_BY_CERTIFICATE_ID = "SELECT tag_id FROM tag_certificate " +
            "WHERE certificate_id = ?";
    private static final String TAG_CERT_DELETE = "DELETE FROM tag_certificate WHERE certificate_id = ?";
    private static final String TAG_CERT_DELETE_BY_TAG_AND_CERT_ID = "DELETE FROM tag_certificate WHERE " +
            "tag_id = ? AND certificate_id = ?";
    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert simpleJdbcInsert;
    private final TagDao tagDao;

    public GiftCertificateDaoImpl(JdbcTemplate jdbcTemplate, TagDao tagDao){
        this.tagDao = tagDao;
        this.jdbcTemplate = jdbcTemplate;
        simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getDataSource())
                .withTableName(TABLE_CERTIFICATE)
                .usingGeneratedKeyColumns(ID);
    }
    @Override
    public long add(GiftCertificate certificate) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(NAME, certificate.getName());
        parameters.put(DESCRIPTION, certificate.getDescription());
        parameters.put(PRICE, certificate.getPrice());
        parameters.put(CREATE_DATE, convertToUtc(certificate.getCreateDate()));
        parameters.put(LAST_UPDATE_DATE, convertToUtc(certificate.getLastUpdateDate()));
        parameters.put(DURATION, certificate.getDuration().toDays());
        long certificateId = simpleJdbcInsert.executeAndReturnKey(parameters).longValue();

        for(Tag tag : certificate.getTagList()){
            addTagIdCertId(tag.getId(), certificateId);
        }

        return certificateId;
    }

    @Override
    public List<GiftCertificate> findAllWithTags(){
        List<GiftCertificate> certificateList = jdbcTemplate.query(SELECT_ALL, giftMapper);
        for(GiftCertificate certificate : certificateList){
            List<Long> listTagId = findByCertificateId(certificate.getId());
            for(Long id : listTagId){
                Tag tag = tagDao.findById(id);
                certificate.addTag(tag);
            }
        }
        return certificateList;
    }

    @Override
    public List<GiftCertificate> findAll(){
        return jdbcTemplate.query(SELECT_ALL, giftMapper);
    }

    @Override
    public List<GiftCertificate> findByTag(long tagId){
        List<GiftCertificate> certificates = new ArrayList<>();
        List<Long> certificatesId = findByTagId(tagId);
        for(Long certId : certificatesId){
            certificates.add(findById(certId));
        }
        return certificates;
    }

    @Override
    public GiftCertificate findById(long id) {
        return selectByParameter(SELECT_BY_ID, id);
    }

    @Override
    public GiftCertificate findByName(String name) {     //TODO (DB function call) + return list
        return selectByParameter(SELECT_BY_NAME, name);
    }

    @Override
    public List<GiftCertificate> findByDescription(String description) {  //TODO (DB function call) + return list
        return jdbcTemplate.query(SELECT_BY_DESCRIPTION, giftMapper, description);
    }

    @Override
    public boolean update(GiftCertificate certificate) {
        // check in service
//        List<Tag> updatedTagList = certificate.getTagList();
//        List<Tag> originalTagList = new ArrayList<>();
//        for(Long id : findByCertificateId(certificate.getId())){
//            Optional<Tag> optionalTag = tagDao.findById(id);
//            if(optionalTag.isPresent()){
//                originalTagList.add(optionalTag.get());
//            }
//        }
//
//        if(!updatedTagList.containsAll(originalTagList)){
//            for(Tag tag : originalTagList){
//                if(!updatedTagList.contains(tag)){
//                    deleteTagByTagIdCertId(certificate.getId(), tag.getId());
//                }
//            }
//        }
//
//        if(!originalTagList.containsAll(updatedTagList)){
//            for(Tag tag: updatedTagList){
//                if(!originalTagList.contains(tag)){
//                    long tagId = tag.getId();
//                    if(!tagDao.findById(tag.getId()).isPresent()){
//                        tagId = tagDao.add(tag);
//                    }
//                    addTagIdCertId(tagId, certificate.getId());
//                }
//            }
//        }

        int rows = jdbcTemplate.update(UPDATE, certificate.getName(), certificate.getDescription(),
                certificate.getPrice(), convertToUtc(certificate.getLastUpdateDate()),
                certificate.getDuration().getSeconds());    //todo named param?
        return rows > 0;
    }

    @Override
    public boolean delete(long id) {
        int rows = jdbcTemplate.update(DELETE_BY_ID, id);
        return rows > 0;
    }

    private <T> GiftCertificate selectByParameter(String sql, T param){
        GiftCertificate certificate = jdbcTemplate.queryForObject(sql, giftMapper, param);
        List<Long> listTagId = findByCertificateId(certificate.getId());
        for(Long tagId : listTagId){
            Tag tag = tagDao.findById(tagId);
            certificate.addTag(tag);
        }
        return certificate;
    }

    private void addTagIdCertId(long tagId, long certificateId){
        jdbcTemplate.update(TAG_CERT_INSERT, tagId, certificateId);
    }

    private List<Long> findByTagId(long tagId) {
        return jdbcTemplate.queryForList(TAG_CERT_SELECT_BY_TAG_ID, Long.class, tagId);
    }

    private List<Long> findByCertificateId(long certificateId) {
        return jdbcTemplate.queryForList(TAG_CERT_SELECT_BY_CERTIFICATE_ID, Long.class, certificateId);
    }

    private boolean deleteTagByTagIdCertId(long certificateId, long tagId) {
        int rows = jdbcTemplate.update(TAG_CERT_DELETE_BY_TAG_AND_CERT_ID, tagId, certificateId);
        return rows == 1;
    }

    private boolean deleteAllTagsByCertificateId(long certificateId) {
        int rows = jdbcTemplate.update(TAG_CERT_DELETE, certificateId);
        return rows == 1;
    }

    private ZonedDateTime convertToUtc(ZonedDateTime zonedDateTime){
        return zonedDateTime.withZoneSameInstant(ZoneOffset.UTC);
    }
}
