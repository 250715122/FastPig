package com.gt.vector;

import ai.onnxruntime.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Embedding 服务 - 使用 ONNX Runtime 加载本地模型进行文本向量化
 * 
 * 支持 bge-small-zh-v1.5 模型（512 维向量）
 * 模型文件需放在 models/ 目录下
 */
public class EmbeddingService {
    
    private static final Logger logger = LogManager.getLogger(EmbeddingService.class);
    
    private static final String MODEL_DIR = "models";
    private static final String MODEL_FILE = "bge-small-zh-v1.5.onnx";
    private static final String VOCAB_FILE = "vocab.txt";
    private static final int VECTOR_DIM = 512;
    private static final int MAX_SEQ_LENGTH = 512;
    
    // 模型下载 URL（使用 Xenova 转换的 ONNX 版本）
    private static final String MODEL_URL = "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx";
    private static final String VOCAB_URL = "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/vocab.txt";
    
    private static EmbeddingService instance;
    
    private OrtEnvironment env;
    private OrtSession session;
    private Map<String, Integer> vocab;
    private boolean available = false;
    private String errorMessage;
    
    // 特殊 token IDs
    private int clsTokenId = 101;  // [CLS]
    private int sepTokenId = 102;  // [SEP]
    private int padTokenId = 0;    // [PAD]
    private int unkTokenId = 100;  // [UNK]
    
    private EmbeddingService() {
        // 私有构造
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized EmbeddingService getInstance() {
        if (instance == null) {
            instance = new EmbeddingService();
        }
        return instance;
    }
    
    /**
     * 初始化服务（加载模型）
     */
    public synchronized void initialize() {
        if (available) {
            return;
        }
        
        try {
            Path modelPath = getModelPath();
            Path vocabPath = getVocabPath();
            
            if (!Files.exists(modelPath) || !Files.exists(vocabPath)) {
                errorMessage = "模型文件不存在，请先下载模型";
                logger.warn(errorMessage);
                return;
            }
            
            // 加载词汇表
            loadVocab(vocabPath);
            
            // 初始化 ONNX Runtime
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            
            session = env.createSession(modelPath.toString(), opts);
            
            available = true;
            logger.info("模型加载成功，向量维度: {}", VECTOR_DIM);
            
        } catch (Exception e) {
            errorMessage = "模型加载失败: " + e.getMessage();
            logger.error(errorMessage, e);
        }
    }
    
    /**
     * 文本转向量
     * @param text 输入文本
     * @return 512 维向量，失败返回 null
     */
    public float[] embed(String text) {
        if (!available || session == null) {
            logger.error("服务不可用");
            return null;
        }
        
        try {
            // Tokenize
            int[] inputIds = tokenize(text);
            int seqLen = inputIds.length;
            
            // 创建 attention mask（全 1）
            long[] attentionMask = new long[seqLen];
            Arrays.fill(attentionMask, 1L);
            
            // 创建 token type ids（全 0）
            long[] tokenTypeIds = new long[seqLen];
            
            // 转换为 long 数组
            long[] inputIdsLong = new long[seqLen];
            for (int i = 0; i < seqLen; i++) {
                inputIdsLong[i] = inputIds[i];
            }
            
            // 创建输入张量（batch_size=1）
            // ONNX Runtime 直接接受 2D 数组
            long[][] inputIds2D = new long[1][seqLen];
            long[][] attentionMask2D = new long[1][seqLen];
            long[][] tokenTypeIds2D = new long[1][seqLen];
            
            System.arraycopy(inputIdsLong, 0, inputIds2D[0], 0, seqLen);
            System.arraycopy(attentionMask, 0, attentionMask2D[0], 0, seqLen);
            System.arraycopy(tokenTypeIds, 0, tokenTypeIds2D[0], 0, seqLen);
            
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, inputIds2D);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, attentionMask2D);
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIds2D);
            
            // 准备输入
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            
            // 运行推理
            OrtSession.Result result = session.run(inputs);
            
            // 获取输出 (last_hidden_state 或 sentence_embedding)
            // bge 模型输出格式：[batch_size, seq_len, hidden_size] 或 [batch_size, hidden_size]
            float[] embedding = extractEmbedding(result);
            
            // 清理资源
            inputIdsTensor.close();
            attentionMaskTensor.close();
            tokenTypeIdsTensor.close();
            result.close();
            
            return embedding;
            
        } catch (Exception e) {
            logger.error("向量化失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 批量文本转向量
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            float[] vec = embed(text);
            results.add(vec);
        }
        return results;
    }
    
    /**
     * 检查模型是否可用
     */
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * 检查模型文件是否存在
     */
    public boolean isModelDownloaded() {
        return Files.exists(getModelPath()) && Files.exists(getVocabPath());
    }
    
    /**
     * 获取错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * 获取向量维度
     */
    public int getVectorDimension() {
        return VECTOR_DIM;
    }
    
    /**
     * 下载模型文件
     * @param progressCallback 进度回调 (0.0 ~ 1.0)
     * @return 是否成功
     */
    public boolean downloadModel(Consumer<Double> progressCallback) {
        try {
            Path modelDir = Paths.get(System.getProperty("user.dir"), MODEL_DIR);
            Files.createDirectories(modelDir);
            
            Path modelPath = modelDir.resolve(MODEL_FILE);
            Path vocabPath = modelDir.resolve(VOCAB_FILE);
            
            // 下载模型文件
            logger.info("开始下载模型: {}", MODEL_URL);
            if (!downloadFile(MODEL_URL, modelPath, progress -> {
                if (progressCallback != null) {
                    progressCallback.accept(progress * 0.9); // 模型占 90%
                }
            })) {
                return false;
            }
            
            // 下载词汇表
            logger.info("开始下载词汇表: {}", VOCAB_URL);
            if (!downloadFile(VOCAB_URL, vocabPath, progress -> {
                if (progressCallback != null) {
                    progressCallback.accept(0.9 + progress * 0.1); // 词汇表占 10%
                }
            })) {
                return false;
            }
            
            logger.info("模型下载完成");
            return true;
            
        } catch (Exception e) {
            logger.error("下载失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 关闭服务
     */
    public synchronized void close() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            available = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // ==================== 私有方法 ====================
    
    private Path getModelPath() {
        return Paths.get(System.getProperty("user.dir"), MODEL_DIR, MODEL_FILE);
    }
    
    private Path getVocabPath() {
        return Paths.get(System.getProperty("user.dir"), MODEL_DIR, VOCAB_FILE);
    }
    
    /**
     * 加载词汇表
     */
    private void loadVocab(Path vocabPath) throws IOException {
        vocab = new HashMap<>();
        List<String> lines = Files.readAllLines(vocabPath);
        for (int i = 0; i < lines.size(); i++) {
            vocab.put(lines.get(i).trim(), i);
        }
        
        // 更新特殊 token IDs
        if (vocab.containsKey("[CLS]")) clsTokenId = vocab.get("[CLS]");
        if (vocab.containsKey("[SEP]")) sepTokenId = vocab.get("[SEP]");
        if (vocab.containsKey("[PAD]")) padTokenId = vocab.get("[PAD]");
        if (vocab.containsKey("[UNK]")) unkTokenId = vocab.get("[UNK]");
        
        logger.info("词汇表加载完成，共 {} 词", vocab.size());
    }
    
    /**
     * 简单的中文 BERT Tokenizer
     * 按字符切分（中文）+ WordPiece（英文）
     */
    private int[] tokenize(String text) {
        List<Integer> tokens = new ArrayList<>();
        tokens.add(clsTokenId); // [CLS]
        
        // 预处理：转小写，去除多余空格
        text = text.toLowerCase().trim();
        
        // 按字符遍历
        int i = 0;
        while (i < text.length() && tokens.size() < MAX_SEQ_LENGTH - 1) {
            char c = text.charAt(i);
            
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            
            // 中文字符：单独作为一个 token
            if (isChinese(c)) {
                String charStr = String.valueOf(c);
                tokens.add(vocab.getOrDefault(charStr, unkTokenId));
                i++;
            }
            // 英文/数字：尝试匹配最长的词
            else if (Character.isLetterOrDigit(c)) {
                StringBuilder word = new StringBuilder();
                while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                    word.append(text.charAt(i));
                    i++;
                }
                // WordPiece 分词
                tokenizeWordPiece(word.toString(), tokens);
            }
            // 标点符号
            else {
                String charStr = String.valueOf(c);
                tokens.add(vocab.getOrDefault(charStr, unkTokenId));
                i++;
            }
        }
        
        tokens.add(sepTokenId); // [SEP]
        
        // 转换为数组
        int[] result = new int[tokens.size()];
        for (int j = 0; j < tokens.size(); j++) {
            result[j] = tokens.get(j);
        }
        return result;
    }
    
    /**
     * WordPiece 分词（用于英文单词）
     */
    private void tokenizeWordPiece(String word, List<Integer> tokens) {
        if (vocab.containsKey(word)) {
            tokens.add(vocab.get(word));
            return;
        }
        
        // 尝试分解
        int start = 0;
        while (start < word.length()) {
            int end = word.length();
            String subword = null;
            
            while (start < end) {
                String candidate = word.substring(start, end);
                if (start > 0) {
                    candidate = "##" + candidate;
                }
                
                if (vocab.containsKey(candidate)) {
                    subword = candidate;
                    break;
                }
                end--;
            }
            
            if (subword == null) {
                // 找不到匹配，使用 [UNK]
                tokens.add(unkTokenId);
                break;
            }
            
            tokens.add(vocab.get(subword));
            start = end;
        }
    }
    
    /**
     * 判断是否为中文字符
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }
    
    /**
     * 将 1D 数组重塑为 2D
     */
    private long[][] reshape2D(long[] arr, int rows, int cols) {
        long[][] result = new long[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(arr, i * cols, result[i], 0, cols);
        }
        return result;
    }
    
    /**
     * 从 ONNX 输出中提取 embedding
     * bge 模型使用 [CLS] token 的隐藏状态作为句子表示
     */
    private float[] extractEmbedding(OrtSession.Result result) throws OrtException {
        // 尝试获取 sentence_embeddings（如果模型导出时包含）
        if (result.get("sentence_embedding").isPresent()) {
            float[][] output = (float[][]) result.get("sentence_embedding").get().getValue();
            return output[0];
        }
        
        // 否则使用 last_hidden_state 的 [CLS] token
        if (result.get("last_hidden_state").isPresent()) {
            float[][][] output = (float[][][]) result.get("last_hidden_state").get().getValue();
            // output shape: [batch, seq_len, hidden_size]
            // 取 [0][0] 即 [CLS] token 的表示
            return output[0][0];
        }
        
        // 遍历所有输出
        for (Map.Entry<String, OnnxValue> entry : result) {
            OnnxValue value = entry.getValue();
            if (value != null) {
                Object data = value.getValue();
                if (data instanceof float[][]) {
                    return ((float[][]) data)[0];
                } else if (data instanceof float[][][]) {
                    return ((float[][][]) data)[0][0];
                }
            }
        }
        
        throw new RuntimeException("无法从模型输出中提取 embedding");
    }
    
    /**
     * 下载文件
     */
    private boolean downloadFile(String urlStr, Path targetPath, Consumer<Double> progressCallback) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                logger.error("下载失败，HTTP {}", responseCode);
                return false;
            }
            
            long totalSize = conn.getContentLengthLong();
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(targetPath)) {
                
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    
                    if (totalSize > 0 && progressCallback != null) {
                        progressCallback.accept((double) downloaded / totalSize);
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("下载异常: {}", e.getMessage(), e);
            return false;
        }
    }
}

