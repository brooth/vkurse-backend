/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.platform.storage.application.StoredFileService;
import space.vkurse.platform.storage.domain.FilePurpose;
import space.vkurse.platform.storage.domain.Upload;
import space.vkurse.platform.user.domain.User;
import space.vkurse.platform.user.domain.UserRepository;

/**
 * A user's own picture: a public image. The client asks for an upload, sends the image to storage with the link, and
 * then sets it as the picture.
 */
@Service
public class UserPictureService {
    private final UserRepository users;
    private final StoredFileService files;
    private final TransactionTemplate transaction;

    public UserPictureService(
            UserRepository users, StoredFileService files, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Upload requestUpload(UUID userId, String name, String contentType, long size) {
        return files.requestUpload(FilePurpose.USER_PICTURE, null, userId, name, contentType, size);
    }

    /**
     * Makes an image the caller uploaded their picture; the uploaded picture it replaces is deleted. The upload is
     * checked against storage first, outside the transaction that changes the user.
     */
    public User change(UUID userId, UUID fileId) {
        var file = files.confirm(fileId, userId, FilePurpose.USER_PICTURE, null);
        var url = files.download(file).url().toString();
        return Objects.requireNonNull(transaction.execute(status -> {
            var user = get(userId);
            var replaced = user.changePicture(file.getId(), url);
            users.save(user);
            if (replaced != null) {
                files.delete(replaced);
            }
            return user;
        }));
    }

    @Transactional
    public void remove(UUID userId) {
        var user = get(userId);
        var removed = user.removePicture();
        users.save(user);
        if (removed != null) {
            files.delete(removed);
        }
    }

    private User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
