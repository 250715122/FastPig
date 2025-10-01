package com.gt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * PNG 转 ICO 转换器
 * 将 PNG 图标转换为 Windows ICO 格式
 */
public class PngToIcoConverter {

    public static void main(String[] args) throws IOException {
        String pngPath = "src/main/resources/icons/FastPig.png";
        String icoPath = "src/main/resources/icons/FastPig.ico";
        
        System.out.println("开始转换 PNG 到 ICO...");
        System.out.println("输入: " + pngPath);
        System.out.println("输出: " + icoPath);
        System.out.println();
        
        // 读取 PNG
        BufferedImage image = ImageIO.read(new File(pngPath));
        
        // 转换为 ICO（简单版本：只包含一个尺寸）
        convertToIco(image, icoPath);
        
        System.out.println("✓ 转换完成！");
        System.out.println();
        System.out.println("ICO 文件已生成: " + icoPath);
        System.out.println("现在可以在 build-exe.bat 中使用此图标文件");
    }

    /**
     * 将 BufferedImage 转换为 ICO 格式
     * ICO 格式说明：https://en.wikipedia.org/wiki/ICO_(file_format)
     */
    private static void convertToIco(BufferedImage image, String outputPath) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width != height) {
            throw new IllegalArgumentException("图标必须是正方形");
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] pngData = baos.toByteArray();
        
        FileOutputStream fos = new FileOutputStream(outputPath);
        DataOutputStream dos = new DataOutputStream(fos);
        
        // ICO 文件头（6 字节）
        dos.writeShort(0);           // Reserved (must be 0)
        dos.writeShort(1);           // Image type: 1 = ICO
        dos.writeShort(1);           // Number of images
        
        // ICO 目录项（16 字节）
        dos.writeByte(width == 256 ? 0 : width);   // Width (0 means 256)
        dos.writeByte(height == 256 ? 0 : height); // Height (0 means 256)
        dos.writeByte(0);            // Color palette (0 = no palette)
        dos.writeByte(0);            // Reserved
        dos.writeShort(1);           // Color planes
        dos.writeShort(32);          // Bits per pixel
        dos.writeInt(pngData.length);// Size of image data
        dos.writeInt(22);            // Offset of image data (6 + 16)
        
        // 写入 PNG 数据
        dos.write(pngData);
        
        dos.close();
        fos.close();
    }
}

