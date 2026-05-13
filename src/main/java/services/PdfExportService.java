package services;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PdfExportService {

    public void exportForumReport(
            String filePath,
            int totalPosts,
            int totalComments,
            int activeUsers,
            int flaggedPosts,
            int postLikes,
            int postDislikes,
            int commentLikes,
            int commentDislikes,
            PostService postService
    ) throws Exception {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));

        document.open();

        Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD);
        Font sectionFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 12);

        document.add(new Paragraph("EchoCare Forum Statistics Report", titleFont));
        document.add(new Paragraph(
                "Generated at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                normalFont
        ));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("General Statistics", sectionFont));
        document.add(new Paragraph("Total posts: " + totalPosts, normalFont));
        document.add(new Paragraph("Total comments: " + totalComments, normalFont));
        document.add(new Paragraph("Active forum users: " + activeUsers, normalFont));
        document.add(new Paragraph("Flagged posts: " + flaggedPosts, normalFont));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Reactions", sectionFont));
        document.add(new Paragraph("Post likes: " + postLikes, normalFont));
        document.add(new Paragraph("Post dislikes: " + postDislikes, normalFont));
        document.add(new Paragraph("Comment likes: " + commentLikes, normalFont));
        document.add(new Paragraph("Comment dislikes: " + commentDislikes, normalFont));
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Posts by Category", sectionFont));
        for (PostService.CategoryPostCount item : postService.getPostsByCategory()) {
            document.add(new Paragraph(item.category() + ": " + item.count(), normalFont));
        }
        document.add(new Paragraph(" "));

        document.add(new Paragraph("Posts Activity - Last 30 Days", sectionFont));
        for (PostService.DailyPostCount item : postService.getPostsActivityLast30Days()) {
            document.add(new Paragraph(item.day() + ": " + item.count() + " posts", normalFont));
        }

        document.close();
    }
}