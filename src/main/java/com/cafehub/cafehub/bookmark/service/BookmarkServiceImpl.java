package com.cafehub.cafehub.bookmark.service;

import com.cafehub.cafehub.bookmark.entity.Bookmark;
import com.cafehub.cafehub.bookmark.repository.BookmarkRepository;
import com.cafehub.cafehub.bookmark.request.BookmarkRequestDto;
import com.cafehub.cafehub.bookmark.response.BookmarkedCafeDetails;
import com.cafehub.cafehub.cafe.entity.Cafe;
import com.cafehub.cafehub.cafe.repository.CafeRepository;
import com.cafehub.cafehub.common.ErrorCode;
import com.cafehub.cafehub.common.dto.ResponseDto;
import com.cafehub.cafehub.member.entity.Member;
import com.cafehub.cafehub.security.UserDetailsImpl;
import com.cafehub.cafehub.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkServiceImpl implements BookmarkService {

    //궁금한게.. -> request 다 필요한거 아닌가..?
    private final BookmarkRepository bookmarkRepository;
    private final CafeRepository cafeRepository;
    private final JwtProvider jwtProvider;

    //북마크 조회
    @Override
    public ResponseDto<?> getBookmarkList(HttpServletRequest request) {
        /**
         * 추후 로그인 여부 확인
         */
        Member member = getMemberFromJwt(request);

        //3중 조인으로 해결할 것.
        List<Bookmark> bookmarks = bookmarkRepository.findAllByMemberId(member.getId());
        List<BookmarkedCafeDetails> cafeDetails = bookmarks.stream()
                .map(bookmark -> {
                    Cafe cafe = bookmark.getCafe();
                    return BookmarkedCafeDetails.builder()
                            .cafeId(cafe.getId())
                            .cafeRating(cafe.getRating())
                            .cafeTheme(cafe.getTheme().getName())
                            .cafePhotoUrl(cafe.getCafePhotoUrl())
                            .cafeReviewNum(cafe.getReviewCount())
                            .cafeName(cafe.getName())
                            .build();
                })
                .toList();

        return ResponseDto.success(cafeDetails);
    }

    //북마크 저장
    @Transactional
    @Override
    public ResponseDto<?> saveBookmark(BookmarkRequestDto bookmarkRequestDto, HttpServletRequest request) {

        Long cafeId = bookmarkRequestDto.getCafeId();
        Cafe cafe = cafeRepository.findById(cafeId).get();
        Member member = getMemberFromJwt(request);

        // if(bookmarkRepository.existsByMemberIdAndCafeId(1L, request.getCafeId())) throw new MemberBookmarkAlreadyExistException();

        //이미 북마크에 존재하는 카페인 경우
        if (bookmarkRepository.existsByCafeIdAndMemberId(member.getId(), cafeId)) {
            return ResponseDto.success(cafeId);
        }

        Bookmark bookmark = Bookmark.builder()
                .member(member)
                .cafe(cafe)
                .build();

        bookmarkRepository.save(bookmark);

        Long saveCafeId = bookmark.getCafe().getId();

        return ResponseDto.success(saveCafeId);
    }

    //북마크 삭제
    @Transactional
    @Override
    public ResponseDto<?> deleteBookmark(BookmarkRequestDto bookmarkRequestDto, HttpServletRequest request) {
        /**
         * 추후 로그인 여부 확인
         */
        Member member = getMemberFromJwt(request);

        //Member member = memberRepository.getOne(2l);
        Cafe cafe = cafeRepository.findById(bookmarkRequestDto.getCafeId()).orElse(null);

        //해당하는 cafe가 없는 경우-> 에러내용은 내가 작성?
        if (cafe == null) {
            return ResponseDto.fail(ErrorCode.CAFE_NOT_FOUND);
        }
        //원하는 카페 아이디가 실제 카페 아이디와 다른 경우->?이게 모지, 질문
        //서버에서 가지고 있는 정보와 클라이언트가 원하는 정보가 일치하는지 확인
        else if (!bookmarkRequestDto.getCafeId().equals(cafe.getId())) {
            return ResponseDto.fail(ErrorCode.INVALID_CAFE_ID);
        }
        //일치하는 카페 아이디를 찾은 경우
        else {
            // 상상 이상으로 spring data jpa 가 제공하는 기능이 많음
            bookmarkRepository.deleteByCafeIdAndMemberId(bookmarkRequestDto.getCafeId(), member.getId());
            return ResponseDto.success(bookmarkRequestDto.getCafeId());
        }
    }

    private Member getMemberFromJwt(HttpServletRequest request) {
        Authentication authentication = jwtProvider.getAuthentication(request.getHeader("Authorization").substring(7));
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return userDetails.getMember();
    }

}
