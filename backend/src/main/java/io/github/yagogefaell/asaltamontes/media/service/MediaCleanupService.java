package io.github.yagogefaell.asaltamontes.media.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.media.domain.Media;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.storage.MediaStorage;
import lombok.extern.slf4j.Slf4j;

/**
 * Deletes images (files and row) that no resource references. Used when a picture is replaced and by the
 * scheduled cleanup of uploads that were never linked.
 * <p>
 * Race with linking: {@link MediaReferenceService#link} takes a shared row lock on the media inside the
 * transaction that stores the reference. Here the row is locked exclusively first and the references are checked
 * afterwards (a new statement, so it sees what linking committed): either linking wins and the media is
 * referenced, or this wins and linking finds no media (404).
 */
@Slf4j
@Service
public class MediaCleanupService {

    private final MediaRepository mediaRepository;
    private final MediaStorage storage;
    private final MediaProperties properties;
    private final TransactionTemplate transaction;

    public MediaCleanupService(MediaRepository mediaRepository, MediaStorage storage, MediaProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Deletes the media when nothing references it and, if {@code olderThan} is given, it was created before that.
     * The files are removed after the commit. Returns whether it was deleted.
     */
    public boolean deleteIfUnreferenced(UUID id, Instant olderThan) {
        // A template and not @Transactional: deleteOrphans calls this method on itself, which would skip the proxy
        return Boolean.TRUE.equals(transaction.execute(status -> deleteInTransaction(id, olderThan)));
    }

    private boolean deleteInTransaction(UUID id, Instant olderThan) {
        Media media = mediaRepository.findByIdForUpdate(id).orElse(null);
        if (media == null || (olderThan != null && !media.getCreatedAt().isBefore(olderThan))
                || mediaRepository.isReferenced(id)) {
            return false;
        }
        mediaRepository.delete(media);
        deleteFilesAfterCommit(media.storageKeys());
        return true;
    }

    private void deleteFilesAfterCommit(List<String> keys) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteFiles(keys);
                }
            });
        } else {
            deleteFiles(keys);
        }
    }

    /**
     * Account deletion: removes every image the user uploaded, whoever references it, and clears those references.
     * Joins the caller's transaction (the files are removed after its commit, failures are only logged), so call it
     * from inside the transaction that deletes the account.
     */
    public int deleteAllOwnedBy(Long ownerId) {
        return transaction.execute(status -> {
            mediaRepository.detachAvatarsOf(ownerId);
            mediaRepository.detachCoversOf(ownerId);
            mediaRepository.detachGroupImagesOf(ownerId);
            int deleted = 0;
            List<UUID> batch;
            do {
                batch = mediaRepository.findIdsByOwner(ownerId, PageRequest.of(0, properties.getCleanupBatchSize()));
                for (UUID id : batch) {
                    Media media = mediaRepository.findByIdForUpdate(id).orElse(null);
                    if (media != null) {
                        mediaRepository.delete(media);
                        deleteFilesAfterCommit(media.storageKeys());
                        deleted++;
                    }
                }
                mediaRepository.flush(); // the next page must not return rows that are already deleted
            } while (!batch.isEmpty());
            return deleted;
        });
    }

    /** One cleanup run: orphans older than the grace period, in batches. Returns how many were deleted. */
    public int deleteOrphans() {
        Instant before = Instant.now().minus(properties.getOrphanGracePeriod());
        int batchSize = properties.getCleanupBatchSize();
        int deleted = 0;
        List<UUID> batch;
        int skipped;
        do {
            batch = mediaRepository.findUnreferencedCreatedBefore(before, PageRequest.of(0, batchSize));
            skipped = 0;
            for (UUID id : batch) {
                try {
                    if (deleteIfUnreferenced(id, before)) {
                        deleted++;
                    } else {
                        skipped++; // linked meanwhile
                    }
                } catch (RuntimeException e) {
                    skipped++;
                    log.warn("Could not delete orphan media {}", id, e);
                }
            }
            // Stop when a batch made no progress, otherwise the same rows would come back forever
        } while (batch.size() == batchSize && skipped < batch.size());
        return deleted;
    }

    private void deleteFiles(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Media row deleted but the file {} could not be removed", key, e);
            }
        }
    }
}
