package com.cafehub.cafehub.member.mypage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.cafehub.cafehub.common.ErrorCode;
import com.cafehub.cafehub.common.dto.ResponseDto;
import com.cafehub.cafehub.member.entity.Member;
import com.cafehub.cafehub.member.mypage.dto.ProfileRequestDto;
import com.cafehub.cafehub.member.mypage.dto.ProfileResponseDto;
import com.cafehub.cafehub.member.mypage.exception.FailedChangeProfile;
import com.cafehub.cafehub.member.repository.MemberRepository;
import com.cafehub.cafehub.security.UserDetailsImpl;
import com.cafehub.cafehub.security.jwt.JwtProvider;
import com.cafehub.cafehub.security.jwt.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MyPageService {
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    private final AmazonS3Client s3Client;
    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;
    private final BookmarkRepository bookmarkRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public ProfileResponseDto getMyProfile(HttpServletRequest request) {
        Member member = getMemberFromJwt(request);
        ProfileResponseDto profileResponseDto = ProfileResponseDto.builder()
                .nickname(member.getNickname())
                .email(member.getEmail())
                .profileImg(member.getUserPhotoUrl())
                .build();

        return profileResponseDto;
    }

    public ResponseDto<?> changeMyProfile(HttpServletRequest request, ProfileRequestDto requestDto) {
        Member member = getMemberFromJwt(request);
        String nickname = requestDto.getNickname();
        Object profileImg = requestDto.getProfileImg();
        try {
            if (nickname != null) {
                member.updateNickname(nickname);
            }
            if (profileImg != null) {
                String userPhotoUrl = uploadS3((MultipartFile) profileImg, member);
                member.updateProfileImg(userPhotoUrl);
            }
            return ResponseDto.success("Profile Changed");
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new FailedChangeProfile(ErrorCode.FAILED_CHANGE_PROFILE);
        }
    }

//    public ResponseDto<?> deleteMember(HttpServletRequest request) {
//        Long memberId = getMemberFromJwt(request).getId();
//        String email = getMemberFromJwt(request).getEmail();
//    }

    private Member getMemberFromJwt(HttpServletRequest request) {
        Authentication authentication = jwtProvider.getAuthentication(request.getHeader("Authorization").substring(7));
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userDetails.getMember();
    }

    private String uploadS3(MultipartFile profileImg, Member member) throws IOException {
        String userPhotoUrl = member.getUserPhotoUrl();
        String basicImg = "아마존S3주소";
        if (userPhotoUrl!=null && !userPhotoUrl.equals(basicImg)) {
            fileDelete(userPhotoUrl);
        }
        String s3FileName = UUID.randomUUID() + "-" + profileImg.getOriginalFilename();
        ObjectMetadata objMeta = new ObjectMetadata();
        objMeta.setContentLength(profileImg.getSize());
        objMeta.setContentType(profileImg.getContentType());
        s3Client.putObject(bucket, s3FileName, profileImg.getInputStream(), objMeta);
        return s3Client.getUrl(bucket, s3FileName).toString();

    }

    private void fileDelete(String userPhotoUrl) {
        try {
            String decodeVal = URLDecoder.decode(userPhotoUrl.substring(51), StandardCharsets.UTF_8);
            s3Client.deleteObject(this.bucket,decodeVal);
        } catch (AmazonServiceException e) {
            log.error(e.getErrorMessage());
        }
    }
}