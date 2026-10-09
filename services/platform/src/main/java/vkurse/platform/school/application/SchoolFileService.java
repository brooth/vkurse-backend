/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vkurse.core.web.Paging;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.Upload;

/**
 * A school's file library: private files its staff upload, to use in lessons. Everyone on the staff may list and
 * download them; who may upload and delete is decided by the member ({@code ensureCanManageFiles}). A file of another
 * school is "not found".
 */
@Service
public class SchoolFileService {
    private final SchoolMemberService staff;
    private final StoredFileService files;

    public SchoolFileService(SchoolMemberService staff, StoredFileService files) {
        this.staff = staff;
        this.files = files;
    }

    public Upload requestUpload(UUID userId, UUID schoolId, String name, String contentType, long size) {
        staff.require(schoolId, userId).ensureCanManageFiles();
        return files.requestUpload(FilePurpose.SCHOOL_FILE, schoolId, userId, name, contentType, size);
    }

    /** Puts a file the caller uploaded into the library, once storage really holds it. */
    public StoredFile confirm(UUID userId, UUID schoolId, UUID fileId) {
        staff.require(schoolId, userId).ensureCanManageFiles();
        return files.confirm(fileId, userId, FilePurpose.SCHOOL_FILE, schoolId);
    }

    /** The library, newest first. {@code $search} looks in the file names. */
    public List<StoredFile> list(UUID userId, UUID schoolId, Paging paging) {
        staff.require(schoolId, userId);
        return files.findBySchool(schoolId, FilePurpose.SCHOOL_FILE, paging.search(), paging.limit(), paging.offset());
    }

    /** A signed link that downloads the file, for a short time. */
    public Download download(UUID userId, UUID schoolId, UUID fileId) {
        staff.require(schoolId, userId);
        return files.download(files.get(schoolId, FilePurpose.SCHOOL_FILE, fileId));
    }

    public void delete(UUID userId, UUID schoolId, UUID fileId) {
        staff.require(schoolId, userId).ensureCanManageFiles();
        files.delete(files.get(schoolId, FilePurpose.SCHOOL_FILE, fileId).getId());
    }
}
