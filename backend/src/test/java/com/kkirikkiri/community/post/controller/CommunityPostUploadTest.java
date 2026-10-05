package com.kkirikkiri.community.post.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.global.security.AuthenticatedUser;

class CommunityPostUploadTest {
	@ParameterizedTest
	@ValueSource(ints = {0, 1, 5, 6})
	void bindsRepeatedImagesPartsAndEnforcesLimit(int count) throws Exception {
		var service = mock(CommunityPostService.class);
		var mvc = MockMvcBuilders.standaloneSetup(new CommunityPostController(service))
				.setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
					public boolean supportsParameter(MethodParameter parameter) {
						return parameter.getParameterType() == AuthenticatedUser.class;
					}
					public Object resolveArgument(MethodParameter p, ModelAndViewContainer c,
							NativeWebRequest r, WebDataBinderFactory f) {
						return new AuthenticatedUser(7L, "USER");
					}
				}).build();
		var request = multipart("/api/posts").file(new MockMultipartFile("request", "blob", "application/json",
				"{\"category\":\"자유게시판\",\"title\":\"제목\",\"content\":\"내용\",\"anonymous\":false}".getBytes(StandardCharsets.UTF_8)));
		for (int i = 0; i < count; i++) {
			request.file(new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1, 2}));
		}
		mvc.perform(request).andExpect(count <= 5 ? status().isOk() : status().isBadRequest());
		if (count <= 5) {
			verify(service).createPost(eq(7L), any(), argThat(images -> images.images().size() == count));
		} else verifyNoInteractions(service);
	}
}
