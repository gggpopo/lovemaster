package com.yupi.yuaiagent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class LoveAppDocumentLoaderTest {

    @Test
    void loadMarkdowns() {
        LoveAppDocumentLoader loveAppDocumentLoader = new LoveAppDocumentLoader(new PathMatchingResourcePatternResolver());
        loveAppDocumentLoader.loadMarkdowns();
    }
}
