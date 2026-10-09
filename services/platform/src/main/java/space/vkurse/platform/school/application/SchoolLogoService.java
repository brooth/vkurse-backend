/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.platform.school.domain.Membership;
import space.vkurse.platform.school.domain.SchoolMemberRepository;
import space.vkurse.platform.school.domain.SchoolRepository;
import space.vkurse.platform.storage.application.StoredFileService;
import space.vkurse.platform.storage.domain.FilePurpose;
import space.vkurse.platform.storage.domain.Upload;

/**
 * A school's logo: a public image that those who may edit the school upload. The client asks for an upload, sends the
 * image to storage with the link, and then sets it as the logo.
 */
@Service
public class SchoolLogoService {
    private final SchoolRepository schools;
    private final SchoolMemberRepository members;
    private final SchoolMemberService staff;
    private final StoredFileService files;
    private final TransactionTemplate transaction;

    public SchoolLogoService(
            SchoolRepository schools,
            SchoolMemberRepository members,
            SchoolMemberService staff,
            StoredFileService files,
            PlatformTransactionManager transactionManager) {
        this.schools = schools;
        this.members = members;
        this.staff = staff;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Upload requestUpload(UUID userId, UUID schoolId, String name, String contentType, long size) {
        staff.require(schoolId, userId).ensureCanEditSchool();
        return files.requestUpload(FilePurpose.SCHOOL_LOGO, schoolId, userId, name, contentType, size);
    }

    /**
     * Makes an image the caller uploaded the school's logo; the logo it replaces is deleted. The upload is checked
     * against storage first, outside the transaction that changes the school.
     */
    public Membership change(UUID userId, UUID schoolId, UUID fileId) {
        staff.require(schoolId, userId).ensureCanEditSchool();
        var file = files.confirm(fileId, userId, FilePurpose.SCHOOL_LOGO, schoolId);
        var url = files.download(file).url().toString();
        return Objects.requireNonNull(transaction.execute(status -> {
            var membership = editable(userId, schoolId);
            var replaced = membership.school().changeLogo(file.getId(), url);
            schools.save(membership.school());
            if (replaced != null) {
                files.delete(replaced);
            }
            return membership;
        }));
    }

    @Transactional
    public void remove(UUID userId, UUID schoolId) {
        var school = editable(userId, schoolId).school();
        var removed = school.removeLogo();
        schools.save(school);
        if (removed != null) {
            files.delete(removed);
        }
    }

    /** The school with the caller as its member, if they may edit it. Call it in a transaction. */
    private Membership editable(UUID userId, UUID schoolId) {
        var membership =
                members.findMembership(schoolId, userId).orElseThrow(() -> new NotFoundException("School not found"));
        membership.member().ensureCanEditSchool();
        return membership;
    }
}
