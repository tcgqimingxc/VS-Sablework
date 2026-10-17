// 调试配套：打印每个 ogg 的页/包统计，用来解释"为什么有的文件解不出 PCM"。
// 编译：javac -cp <jorbis.jar> -d <out> tools/OggProbe.java
import com.jcraft.jogg.Page;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.Info;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public final class OggProbe {
    public static void main(String[] args) throws Exception {
        for (final String path : args) {
            final SyncState sync = new SyncState();
            final StreamState stream = new StreamState();
            final Page page = new Page();
            final Packet packet = new Packet();
            final Info info = new Info();
            final Comment comment = new Comment();
            sync.init();
            int pages = 0;
            int packets = 0;
            int headers = 0;
            boolean inited = false;
            try (InputStream in = new BufferedInputStream(new FileInputStream(path))) {
                while (true) {
                    final int pr = sync.pageout(page);
                    if (pr < 0) {
                        System.out.println(path + "  pageout<0 (not ogg?)");
                        break;
                    }
                    if (pr == 0) {
                        final int index = sync.buffer(8192);
                        final int bytes = in.read(sync.data, index, 8192);
                        if (bytes <= 0) {
                            break;
                        }
                        sync.wrote(bytes);
                        continue;
                    }
                    pages++;
                    if (!inited) {
                        stream.init(page.serialno());
                        inited = true;
                    }
                    stream.pagein(page);
                    while (true) {
                        final int r = stream.packetout(packet);
                        if (r == 0) {
                            break;
                        }
                        packets++;
                        if (headers < 3 && info.synthesis_headerin(comment, packet) >= 0) {
                            headers++;
                        }
                    }
                    if (page.eos() != 0) {
                        break;
                    }
                }
            }
            System.out.println(path + "  => pages=" + pages + " packets=" + packets + " headers=" + headers
                    + " rate=" + info.rate + " ch=" + info.channels);
        }
    }
}
