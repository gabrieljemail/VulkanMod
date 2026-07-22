package net.vulkanmod.render.chunk.buffer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.memory.buffer.StagingBuffer;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.queue.TransferQueue;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class UploadManager {
    public static UploadManager INSTANCE;

    public static void createInstance() {
        INSTANCE = new UploadManager();
    }

    Queue queue = DeviceManager.getTransferQueue();
    CommandPool.CommandBuffer commandBuffer;

    LongOpenHashSet dstBuffers = new LongOpenHashSet();

    public void submitUploads() {
        if (this.commandBuffer == null)
            return;

        this.queue.submitCommands(this.commandBuffer);

        Synchronization.INSTANCE.addCommandBuffer(this.commandBuffer);

        this.commandBuffer = null;
        this.dstBuffers.clear();
    }

    public void recordUpload(Buffer buffer, long dstOffset, long bufferSize, ByteBuffer src) {
        // UMA fast path: the destination is persistently mapped and host-coherent, so
        // write in place instead of bouncing through the staging buffer + vkCmdCopyBuffer,
        // halving upload memory traffic. No barrier is needed: host writes are made
        // visible by the next vkQueueSubmit, and regions read by frames still in flight
        // are protected by delayed segment freeing (MemoryManager.addToFreeSegment),
        // the same guarantee the transfer-queue path relies on.
        //
        // Not taken while the buffer has a GPU-side write pending in this batch (a
        // growth copy from copyBuffer): that copy executes at submit and would clobber
        // bytes memcpy'd now, so such uploads use the staging path, which is ordered
        // within the same command buffer. dstBuffers resets on submit, and the copy's
        // fence is waited before the next batch records, so this lasts one batch.
        if (Initializer.CONFIG.directUploads && buffer.type.mappable() && !this.dstBuffers.contains(buffer.getId())) {
            buffer.type.copyToBuffer(buffer, src, bufferSize, 0, dstOffset);
            return;
        }

        StagingBuffer stagingBuffer = Vulkan.getStagingBuffer();
        stagingBuffer.copyBuffer((int) bufferSize, src);

        beginCommands();
        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();

        if (!this.dstBuffers.add(buffer.getId())) {
            // WAW hazard: this buffer was already written in this batch. Guard it with
            // a per-buffer barrier instead of a global VK_ACCESS_MEMORY-style one.
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferMemoryBarrier.Buffer barrier = VkBufferMemoryBarrier.calloc(1, stack);
                barrier.sType$Default();
                barrier.buffer(buffer.getId());
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                barrier.size(VK_WHOLE_SIZE);

                vkCmdPipelineBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0,
                        null,
                        barrier,
                        null);
            }
        }

        TransferQueue.uploadBufferCmd(commandBuffer, stagingBuffer.getId(), stagingBuffer.getOffset(), buffer.getId(), dstOffset, bufferSize);
    }

    public void copyBuffer(Buffer src, Buffer dst) {
        copyBuffer(src, 0, dst, 0, src.getBufferSize());
    }

    public void copyBuffer(Buffer src, long srcOffset, Buffer dst, long dstOffset, long size) {
        // Buffer-to-buffer copies (growth/defrag) always run on the GPU, even when both
        // buffers are mappable: on UMA the mapped heap is write-combined, and CPU reads
        // from WC memory are uncached and extremely slow — a CPU-side copy here stalls
        // the render thread for tens of ms on multi-MB buffers. recordUpload defers
        // direct writes to dst while this copy is pending (see dstBuffers check there).
        beginCommands();

        VkCommandBuffer commandBuffer = this.commandBuffer.getHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferMemoryBarrier.Buffer bufferMemoryBarriers = VkBufferMemoryBarrier.calloc(1, stack);
            VkBufferMemoryBarrier bufferMemoryBarrier = bufferMemoryBarriers.get(0);
            bufferMemoryBarrier.sType$Default();
            bufferMemoryBarrier.buffer(src.getId());
            bufferMemoryBarrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            bufferMemoryBarrier.dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            bufferMemoryBarrier.size(VK_WHOLE_SIZE);

            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0,
                    null,
                    bufferMemoryBarriers,
                    null);
        }

        this.dstBuffers.add(dst.getId());

        TransferQueue.uploadBufferCmd(commandBuffer, src.getId(), srcOffset, dst.getId(), dstOffset, size);
    }

    public void syncUploads() {
        submitUploads();

        Synchronization.INSTANCE.waitFences();
    }

    private void beginCommands() {
        if (this.commandBuffer == null)
            this.commandBuffer = queue.beginCommands();
    }

}
