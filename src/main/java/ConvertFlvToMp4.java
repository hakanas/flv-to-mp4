import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;


public class ConvertFlvToMp4 {
    public static void main(String[] args) throws Exception {
        
        if (args.length < 2) {
            System.out.println("Missing input");
            System.exit(-1);
        }

        System.out.println("Converting " + args[0] + " to " + args[1]);

        int ret, streamMappingSize;
        String inputFile = args[0], outputFile = args[1];
        AVFormatContext ifmt_ctx = new AVFormatContext(null);
        AVFormatContext ofmt_ctx = new AVFormatContext(null);

        ret = avformat_open_input(ifmt_ctx,inputFile, av_find_input_format("flv"), null);

        if (ret < 0) {
            System.out.printf("Open video file failed %d\n", ret);
            closeContexts(ifmt_ctx, ofmt_ctx);
            throw new IllegalStateException();
        }

        ret = avformat_find_stream_info(ifmt_ctx, (AVDictionary) null);

        if (ret < 0) {
            closeContexts(ifmt_ctx, ofmt_ctx);
            throw new Exception("avformat_find_stream_info() error " + ret + ": Could not find stream information.");
        }

        // Print detailed information about the input or output format, such as duration, bitrate, streams, container, programs, metadata, side data, codec and time base.
        av_dump_format(ifmt_ctx, 0, inputFile, 0);


        ret = avformat_alloc_output_context2(ofmt_ctx, null, null, outputFile);
        if (ret < 0) {
            ret = AVERROR_UNKNOWN;
            closeContexts(ifmt_ctx, ofmt_ctx);
            throw new Exception("avformat_alloc_output_context2() error " + ret + ": Could not create output context1.");
        }

        streamMappingSize = ifmt_ctx.nb_streams();
        for (int i = 0; i < streamMappingSize; i++) {
            AVStream outStream = avformat_new_stream(ofmt_ctx, null);
            if (outStream == null) {
                ret = AVERROR_UNKNOWN;
                closeContexts(ifmt_ctx, ofmt_ctx);
                throw new Exception("avformat_new_stream() error " + ret + ": Failed allocating output stream.");
            }

            ret = avcodec_parameters_copy(outStream.codecpar(), ifmt_ctx.streams(i).codecpar());
            if (ret < 0) {
                closeContexts(ifmt_ctx, ofmt_ctx);
                throw new Exception("avcodec_parameters_copy() error: Failed to copy codec parameters.");
            }

            outStream.codecpar().codec_tag(0);
        }
        av_dump_format(ofmt_ctx, 0, outputFile, 1);

        AVIOContext pb = new AVIOContext(null);
        ret = avio_open(pb, outputFile, AVIO_FLAG_WRITE);
        if (ret < 0) {
            closeContexts(ifmt_ctx, ofmt_ctx);
            throw new Exception("avio_open() error " + ret + ": Could not open output file: " + outputFile);
        }
        ofmt_ctx.pb(pb);

        ret = avformat_write_header(ofmt_ctx, (AVDictionary) null);
        if (ret < 0) {
            closeContexts(ifmt_ctx, ofmt_ctx);
            throw new Exception("avformat_write_header() error " + ret + ": Error occurred when opening output file");
        }

        AVPacket pkt = new AVPacket();
        while (av_read_frame(ifmt_ctx, pkt) == 0) {

            AVStream inStream = ifmt_ctx.streams(pkt.stream_index());
            AVStream outStream = ofmt_ctx.streams(pkt.stream_index());

            /* copy packet */
            pkt.pts(av_rescale_q_rnd(pkt.pts(), inStream.time_base(), outStream.time_base(), AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
            pkt.dts(av_rescale_q_rnd(pkt.dts(), inStream.time_base(), outStream.time_base(), AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
            pkt.duration(av_rescale_q(pkt.duration(), inStream.time_base(), outStream.time_base()));
            pkt.pos(-1);
            av_write_frame(ofmt_ctx, pkt);
            av_packet_unref(pkt);
        }

        av_write_trailer(ofmt_ctx);

        closeContexts(ifmt_ctx, ofmt_ctx);
    }

    public static void closeContexts(AVFormatContext ifmt_ctx, AVFormatContext ofmt_ctx) {
        avformat_close_input(ifmt_ctx);

        /* close output */
        if (ofmt_ctx != null && ((ofmt_ctx.flags() & AVFMT_NOFILE) > 0) )
            avio_closep(ofmt_ctx.pb()); // 0 on success, an AVERROR < 0 on error.

        avformat_free_context(ofmt_ctx);
    }
}
