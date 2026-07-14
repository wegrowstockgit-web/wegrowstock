package com.invsys.repository;

import com.invsys.domain.PickingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickingTaskRepository extends JpaRepository<PickingTask, UUID> {
    List<PickingTask> findByBatchIdOrderBySequenceOrderAsc(UUID batchId);

    List<PickingTask> findByBatchIdAndStatusOrderBySequenceOrderAsc(UUID batchId, String status);
}
