import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  Check,
  ChevronRight,
  CircleAlert,
  Gift,
  ImagePlus,
  Lightbulb,
  MessageCircle,
  Package,
  PencilLine,
  Send,
  ShieldCheck,
  X,
} from "lucide-react";
import * as S from "./CommunityPostFormPageCss";

const CATEGORIES = [
  { name: "자유게시판", description: "함께 나누는 일상", Icon: MessageCircle },
  { name: "나눔게시판", description: "필요한 물건을 나눠요", Icon: Gift },
  { name: "택배게시판", description: "택배 소식을 전해요", Icon: Package },
  { name: "민원게시판", description: "더 나은 생활을 위해", Icon: ShieldCheck },
];
const MAX_IMAGES = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];

function CommunityPostFormPage({ post, onSave, onCancel }) {
  const isEditing = Boolean(post);
  const [category, setCategory] = useState(post?.category || "자유게시판");
  const [title, setTitle] = useState(post?.title || "");
  const [content, setContent] = useState(post?.content || "");
  const [images, setImages] = useState(() => [...(post?.images || [])]);
  const [errors, setErrors] = useState({});
  const [imageError, setImageError] = useState("");
  const [isProcessing, setIsProcessing] = useState(false);
  const titleRef = useRef(null);
  const contentRef = useRef(null);
  const fileRef = useRef(null);
  const readersRef = useRef(new Set());
  const mountedRef = useRef(false);
  const processingRef = useRef(false);
  const nextImageRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;
    const readers = readersRef.current;

    return () => {
      mountedRef.current = false;
      readers.forEach((reader) => reader.abort());
      readers.clear();
    };
  }, []);

  const clearError = (field) => {
    setErrors((previous) => ({ ...previous, [field]: "" }));
  };

  const readImage = (file) =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      readersRef.current.add(reader);

      const finish = () => readersRef.current.delete(reader);
      reader.onload = () => {
        finish();
        if (typeof reader.result !== "string") {
          reject(new Error("이미지를 읽을 수 없어요. 다른 파일을 선택해 주세요."));
          return;
        }
        nextImageRef.current += 1;
        resolve({
          id: `attachment-${Date.now()}-${nextImageRef.current}`,
          src: reader.result,
          name: file.name,
        });
      };
      reader.onerror = () => {
        finish();
        reject(new Error("이미지를 읽을 수 없어요. 파일을 다시 선택해 주세요."));
      };
      reader.onabort = () => {
        finish();
        reject(new Error("이미지 선택이 취소되었어요."));
      };
      reader.readAsDataURL(file);
    });

  const handleImageChange = async (event) => {
    const files = Array.from(event.target.files || []);
    event.target.value = "";
    if (!files.length || processingRef.current) return;

    setImageError("");
    if (images.length + files.length > MAX_IMAGES) {
      setImageError(`이미지는 최대 ${MAX_IMAGES}장까지 첨부할 수 있어요.`);
      return;
    }
    if (files.some((file) => !IMAGE_TYPES.includes(file.type))) {
      setImageError("JPG, PNG, WebP, GIF 이미지 파일을 선택해 주세요.");
      return;
    }
    if (files.some((file) => file.size > MAX_IMAGE_BYTES || file.size === 0)) {
      setImageError("각 이미지의 용량은 0보다 크고 5MB 이하여야 해요.");
      return;
    }

    processingRef.current = true;
    setIsProcessing(true);
    try {
      const results = await Promise.allSettled(files.map(readImage));
      if (!mountedRef.current) return;
      const failed = results.find((result) => result.status === "rejected");
      if (failed) {
        setImageError(failed.reason.message);
      } else {
        setImages((previous) => [
          ...previous,
          ...results.map((result) => result.value),
        ]);
      }
    } finally {
      processingRef.current = false;
      if (mountedRef.current) setIsProcessing(false);
    }
  };

  const removeImage = (imageId) => {
    if (processingRef.current) return;
    setImages((previous) => previous.filter((image) => image.id !== imageId));
    setImageError("");
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (processingRef.current) return;

    const nextErrors = {};
    if (!title.trim()) nextErrors.title = "게시글 제목을 입력해 주세요.";
    if (!content.trim()) nextErrors.content = "함께 나눌 이야기를 입력해 주세요.";
    setErrors(nextErrors);
    if (nextErrors.title) {
      titleRef.current?.focus();
      return;
    }
    if (nextErrors.content) {
      contentRef.current?.focus();
      return;
    }

    onSave({ category, title: title.trim(), content: content.trim(), images });
  };

  return (
    <S.Page aria-labelledby="community-editor-heading">
      <S.Breadcrumb aria-label="현재 위치">
        <S.BackButton type="button" onClick={onCancel}>
          <ArrowLeft size={17} aria-hidden="true" />
          {isEditing ? "게시글로 돌아가기" : "커뮤니티로 돌아가기"}
        </S.BackButton>
        <ChevronRight size={14} aria-hidden="true" />
        <span aria-current="page">{isEditing ? "글 수정" : "글쓰기"}</span>
      </S.Breadcrumb>

      <S.Intro>
        <S.Eyebrow>
          <PencilLine size={16} aria-hidden="true" /> 우리의 기숙사 이야기
        </S.Eyebrow>
        <S.Title id="community-editor-heading">
          {isEditing ? "이야기를 다듬어 볼까요?" : "어떤 이야기를 나누고 싶나요?"}
        </S.Title>
        <S.Description>
          {isEditing
            ? "내용을 수정하고, 이웃들에게 더 정확한 소식을 전해 주세요."
            : "소소한 일상부터 유용한 정보까지, 기숙사 이웃들과 편하게 나눠요."}
        </S.Description>
      </S.Intro>

      <S.Form noValidate onSubmit={handleSubmit}>
        <S.FormHeader>
          <S.FormTitle>{isEditing ? "게시글 수정" : "새 게시글 작성"}</S.FormTitle>
          <S.RequiredNote>
            <span aria-hidden="true">*</span> 필수 입력
          </S.RequiredNote>
        </S.FormHeader>

        <S.Fields>
          <S.CategoryField>
            <S.Legend>
              카테고리 <S.RequiredMark aria-hidden="true">*</S.RequiredMark>
            </S.Legend>
            <S.CategoryGrid>
              {CATEGORIES.map(({ name, description, Icon }) => (
                <S.CategoryChoice key={name}>
                  <S.CategoryRadio
                    type="radio"
                    name="category"
                    value={name}
                    checked={category === name}
                    onChange={() => setCategory(name)}
                    required
                  />
                  <S.CategoryCard>
                    <Icon size={20} aria-hidden="true" />
                    <strong>{name}</strong>
                    <small>{description}</small>
                    {category === name && (
                      <S.SelectedMark>
                        <Check size={12} aria-hidden="true" />
                      </S.SelectedMark>
                    )}
                  </S.CategoryCard>
                </S.CategoryChoice>
              ))}
            </S.CategoryGrid>
          </S.CategoryField>

          <S.Field>
            <S.FieldHeading>
              <S.Label htmlFor="community-post-title">
                제목 <S.RequiredMark aria-hidden="true">*</S.RequiredMark>
              </S.Label>
              <S.Counter id="community-title-count">{title.length} / 100</S.Counter>
            </S.FieldHeading>
            <S.Input
              ref={titleRef}
              id="community-post-title"
              name="title"
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
                clearError("title");
              }}
              placeholder="이야기가 잘 드러나는 제목을 적어 주세요"
              maxLength={100}
              required
              aria-invalid={Boolean(errors.title)}
              aria-describedby={`community-title-count${errors.title ? " community-title-error" : ""}`}
            />
            {errors.title && (
              <S.Error id="community-title-error" role="alert">
                <CircleAlert size={15} aria-hidden="true" />{errors.title}
              </S.Error>
            )}
          </S.Field>

          <S.Field>
            <S.FieldHeading>
              <S.Label htmlFor="community-post-content">
                내용 <S.RequiredMark aria-hidden="true">*</S.RequiredMark>
              </S.Label>
              <S.Counter id="community-content-count">
                {content.length.toLocaleString()} / 5,000
              </S.Counter>
            </S.FieldHeading>
            <S.Textarea
              ref={contentRef}
              id="community-post-content"
              name="content"
              value={content}
              onChange={(event) => {
                setContent(event.target.value);
                clearError("content");
              }}
              placeholder={"이웃들에게 들려주고 싶은 이야기를 적어 주세요.\n서로를 배려하는 따뜻한 한마디가 좋은 커뮤니티를 만들어요."}
              maxLength={5000}
              required
              aria-invalid={Boolean(errors.content)}
              aria-describedby={`community-content-count${errors.content ? " community-content-error" : ""}`}
            />
            {errors.content && (
              <S.Error id="community-content-error" role="alert">
                <CircleAlert size={15} aria-hidden="true" />{errors.content}
              </S.Error>
            )}
          </S.Field>

          <S.ImageField>
            <S.Legend>
              이미지 첨부 <S.Optional>선택</S.Optional>
            </S.Legend>
            <S.UploadArea>
              <S.UploadIcon>
                <ImagePlus size={24} aria-hidden="true" />
              </S.UploadIcon>
              <S.UploadCopy>
                <strong>사진으로 이야기를 더 생생하게</strong>
                <p id="community-image-help">최대 5장 · 장당 5MB 이하 · JPG, PNG, WebP, GIF</p>
              </S.UploadCopy>
              <S.UploadButton
                type="button"
                onClick={() => fileRef.current?.click()}
                disabled={isProcessing || images.length >= MAX_IMAGES}
                aria-describedby="community-image-help"
              >
                <ImagePlus size={17} aria-hidden="true" />
                {images.length >= MAX_IMAGES ? "5장 첨부 완료" : "이미지 선택"}
              </S.UploadButton>
              <S.FileInput
                ref={fileRef}
                type="file"
                accept={IMAGE_TYPES.join(",")}
                multiple
                name="images"
                aria-label="첨부할 이미지 선택"
                onChange={handleImageChange}
                disabled={isProcessing || images.length >= MAX_IMAGES}
              />
            </S.UploadArea>

            {isProcessing && (
              <S.ImageStatus role="status">
                이미지를 준비하고 있어요. 잠시만 기다려 주세요.
              </S.ImageStatus>
            )}
            {imageError && (
              <S.Error role="alert">
                <CircleAlert size={15} aria-hidden="true" />{imageError}
              </S.Error>
            )}

            {images.length > 0 ? (
              <>
                <S.ImageCount>
                  첨부한 이미지 <strong>{images.length}</strong> / {MAX_IMAGES}
                </S.ImageCount>
                <S.ImageGrid aria-label="첨부한 이미지">
                  {images.map((image, index) => (
                    <S.ImageItem key={image.id}>
                      <S.ImagePreview>
                        <img src={image.src} alt={image.name || `첨부 이미지 ${index + 1}`} />
                        <S.RemoveImage
                          type="button"
                          onClick={() => removeImage(image.id)}
                          disabled={isProcessing}
                          aria-label={`${image.name || `이미지 ${index + 1}`} 삭제`}
                        >
                          <X size={16} aria-hidden="true" />
                        </S.RemoveImage>
                      </S.ImagePreview>
                      <S.ImageName title={image.name || `이미지 ${index + 1}`}>
                        {image.name || `이미지 ${index + 1}`}
                      </S.ImageName>
                    </S.ImageItem>
                  ))}
                </S.ImageGrid>
              </>
            ) : (
              <S.ImageHint>이미지 없이 글만 작성해도 괜찮아요.</S.ImageHint>
            )}
          </S.ImageField>

          <S.Guide>
            <Lightbulb size={20} aria-hidden="true" />
            <div>
              <strong>서로에게 편안한 공간을 함께 만들어요</strong>
              <p>
                다른 사람의 개인정보와 호실이 드러나지 않도록 확인해 주세요.
                따뜻하고 존중하는 표현으로 이야기를 나눠 주세요.
              </p>
            </div>
          </S.Guide>
        </S.Fields>

        <S.Actions>
          <S.ActionHint>작성한 글은 커뮤니티 이웃들에게 공개돼요.</S.ActionHint>
          <S.ActionButtons>
            <S.CancelButton type="button" onClick={onCancel}>
              취소
            </S.CancelButton>
            <S.SaveButton type="submit" disabled={isProcessing}>
              {isEditing
                ? <Check size={18} aria-hidden="true" />
                : <Send size={17} aria-hidden="true" />}
              {isEditing ? "수정 완료" : "게시글 등록"}
            </S.SaveButton>
          </S.ActionButtons>
        </S.Actions>
      </S.Form>
    </S.Page>
  );
}

export default CommunityPostFormPage;
