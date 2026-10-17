// 用 jorbis（Minecraft 客户端自带的 ogg 解码器，libraries/org/jcraft/jorbis）把 .ogg 解成裸 PCM(s16le)，
// 好让 tools/sfx_stats.py 用 numpy 算包络/频谱——本机没有 ffmpeg，WinRT 转码又不认这些文件，
// 而挑音效时"这一下是 0.1 秒的咔哒还是 3 秒的气流"必须靠数字而不是靠文件名。
//
// 编译：javac -cp <jorbis.jar> -d <out> tools/DecodeOgg.java
// 运行：java -cp <jorbis.jar>;<out> DecodeOgg <in.ogg> <out.pcm>
// stdout: "rate channels frames bytes"
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class DecodeOgg {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: DecodeOgg <in.ogg> <out.pcm>");
            System.exit(2);
        }
        final SyncState sync = new SyncState();
        final StreamState stream = new StreamState();
        final Page page = new Page();
        final Packet packet = new Packet();
        final Info info = new Info();
        final Comment comment = new Comment();
        final DspState dsp = new DspState();
        final Block block = new Block(dsp);
        final float[][][] pcmRef = new float[1][][];

        sync.init();
        int[] channelOffsets = new int[1];
        long frames = 0;
        int headerCount = 0;
        boolean streamInited = false;
        boolean decoding = false;

        try (InputStream in = new BufferedInputStream(new FileInputStream(args[0]));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(args[1]))) {
            final byte[] conv = new byte[8192];
            while (true) {
                // 先把缓冲区里已有的页全部取完（一个小文件可能一次就读进好几页），
                // 没页可取时再补数据；读不到新数据才结束。写成"读一次/取一页"会丢尾部页。
                final int pageResult = sync.pageout(page);
                if (pageResult < 0) {
                    throw new IllegalStateException("not an ogg bitstream: " + args[0]);
                }
                if (pageResult == 0) {
                    final int index = sync.buffer(8192);
                    final int bytes = in.read(sync.data, index, 8192);
                    if (bytes <= 0) {
                        break;                          // 输入读完
                    }
                    sync.wrote(bytes);
                    continue;
                }
                if (!streamInited) {
                    stream.init(page.serialno());
                    streamInited = true;
                }
                if (stream.pagein(page) < 0) {
                    throw new IllegalStateException("pagein failed in " + args[0]);
                }
                while (true) {
                    final int result = stream.packetout(packet);
                    if (result == 0) {
                        break;
                    }
                    if (result < 0) {
                        throw new IllegalStateException("packetout failed in " + args[0]);
                    }
                    if (!decoding) {
                        if (info.synthesis_headerin(comment, packet) < 0) {
                            throw new IllegalStateException("expected a header packet in " + args[0]);
                        }
                        if (++headerCount == 3) {
                            dsp.synthesis_init(info);
                            block.init(dsp);
                            channelOffsets = new int[info.channels];
                            decoding = true;
                        }
                        continue;
                    }
                    if (block.synthesis(packet) == 0) {
                        dsp.synthesis_blockin(block);
                    }
                    int samples;
                    while ((samples = dsp.synthesis_pcmout(pcmRef, channelOffsets)) > 0) {
                        final float[][] pcm = pcmRef[0];
                        final int count = Math.min(samples, 4096);
                        int ptr = 0;
                        for (int ch = 0; ch < info.channels; ch++) {
                            final int base = channelOffsets[ch];
                            for (int i = 0; i < count; i++) {
                                int v = (int) (pcm[ch][base + i] * 32767.0f);
                                if (v > 32767) {
                                    v = 32767;
                                } else if (v < -32768) {
                                    v = -32768;
                                }
                                conv[ptr++] = (byte) (v & 0xFF);          // 小端 s16
                                conv[ptr++] = (byte) ((v >> 8) & 0xFF);
                            }
                        }
                        out.write(conv, 0, ptr);
                        frames += count;
                        dsp.synthesis_read(count);
                    }
                }
                if (page.eos() != 0) {
                    break;
                }
            }
        }
        System.out.println(info.rate + " " + info.channels + " " + frames + " " + (frames * 2L * info.channels));
    }
}
