package com.wrj.platform.controller;

import com.wrj.platform.common.ApiResponse;
import com.wrj.platform.common.OpLog;
import com.wrj.platform.entity.Device;
import com.wrj.platform.entity.FlightTask;
import com.wrj.platform.entity.Pilot;
import com.wrj.platform.repository.DeviceRepository;
import com.wrj.platform.repository.FlightTaskRepository;
import com.wrj.platform.repository.PilotRepository;
import com.wrj.platform.service.FlightSimulator;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class FlightTaskController {

    private final FlightTaskRepository taskRepository;
    private final DeviceRepository deviceRepository;
    private final PilotRepository pilotRepository;
    private final FlightSimulator simulator;

    public FlightTaskController(FlightTaskRepository taskRepository,
                                DeviceRepository deviceRepository,
                                PilotRepository pilotRepository,
                                FlightSimulator simulator) {
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.pilotRepository = pilotRepository;
        this.simulator = simulator;
    }

    @GetMapping
    public ApiResponse<List<FlightTask>> list(@RequestParam(required = false) String status) {
        List<FlightTask> all = taskRepository.findAll();
        if (status == null || status.isBlank()) {
            return ApiResponse.ok(all);
        }
        return ApiResponse.ok(all.stream()
                .filter(t -> t.getStatus().name().equals(status))
                .toList());
    }

    /** 下发任务:审批通过 + 状态 FLYING + 启动模拟器 */
    @PostMapping("/{id}/launch")
    @OpLog(module = "飞行任务", action = "下发起飞")
    public ApiResponse<FlightTask> launch(@PathVariable Long id) {
        FlightTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        if (task.getStatus() == FlightTask.Status.FLYING) {
            throw new IllegalArgumentException("任务已在执行中");
        }
        if (task.getApproval() != FlightTask.Approval.APPROVED) {
            throw new IllegalArgumentException("任务未审批通过,禁止起飞");
        }
        Device device = task.getDevice();
        if (device == null) {
            throw new IllegalArgumentException("任务未绑定无人机");
        }
        if (device.getStatus() == Device.Status.MAINTENANCE || device.getStatus() == Device.Status.OFFLINE) {
            throw new IllegalArgumentException("无人机当前不可用: " + device.getStatus());
        }

        task.setStatus(FlightTask.Status.FLYING);
        task.setStartTime(LocalDateTime.now());
        taskRepository.save(task);

        device.setStatus(Device.Status.FLYING);
        deviceRepository.save(device);

        simulator.startTask(task);
        return ApiResponse.ok(task);
    }

    /** 任务中止:无人机返航待命 */
    @PostMapping("/{id}/abort")
    @OpLog(module = "飞行任务", action = "中止任务")
    public ApiResponse<FlightTask> abort(@PathVariable Long id) {
        FlightTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        if (task.getStatus() != FlightTask.Status.FLYING) {
            throw new IllegalArgumentException("任务未在执行中");
        }
        task.setStatus(FlightTask.Status.ABORTED);
        task.setEndTime(LocalDateTime.now());
        taskRepository.save(task);

        Device device = task.getDevice();
        if (device != null) {
            device.setStatus(Device.Status.IDLE);
            deviceRepository.save(device);
        }
        simulator.stopTask(task.getId());
        return ApiResponse.ok(task);
    }

    @PostMapping("/{id}/approve")
    @OpLog(module = "飞行任务", action = "审批")
    public ApiResponse<FlightTask> approve(@PathVariable Long id, @RequestParam String result) {
        FlightTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        FlightTask.Approval approval = switch (result) {
            case "approved" -> FlightTask.Approval.APPROVED;
            case "rejected" -> FlightTask.Approval.REJECTED;
            default -> throw new IllegalArgumentException("非法审批结果: " + result);
        };
        task.setApproval(approval);
        return ApiResponse.ok(taskRepository.save(task));
    }

    @PostMapping
    @OpLog(module = "飞行任务", action = "新增")
    public ApiResponse<FlightTask> create(@RequestBody FlightTask body) {
        if (body.getName() == null || body.getName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (body.getDevice() == null || body.getDevice().getId() == null) {
            throw new IllegalArgumentException("必须选择无人机");
        }
        Device device = deviceRepository.findById(body.getDevice().getId())
                .orElseThrow(() -> new IllegalArgumentException("无人机不存在"));
        Pilot pilot = body.getPilot() != null && body.getPilot().getId() != null
                ? pilotRepository.findById(body.getPilot().getId()).orElse(null)
                : device.getPilot();
        if (pilot == null) {
            throw new IllegalArgumentException("必须指定飞手(任务或无人机绑定)");
        }
        body.setDevice(device);
        body.setPilot(pilot);
        body.setStatus(FlightTask.Status.PENDING);
        body.setApproval(FlightTask.Approval.PENDING);
        return ApiResponse.ok(taskRepository.save(body));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "飞行任务", action = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        FlightTask task = taskRepository.findById(id).orElse(null);
        if (task != null) {
            if (task.getStatus() == FlightTask.Status.FLYING) {
                throw new IllegalArgumentException("执行中的任务不可删除,请先中止");
            }
            taskRepository.deleteById(id);
        }
        return ApiResponse.ok();
    }
}
