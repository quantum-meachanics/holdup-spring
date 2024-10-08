package com.quantum.holdup.service;

import com.quantum.holdup.Page.Pagination;
import com.quantum.holdup.Page.PagingButtonInfo;
import com.quantum.holdup.domain.dto.CreateReportDTO;
import com.quantum.holdup.domain.dto.ReportDTO;
import com.quantum.holdup.domain.dto.ReportDetailDTO;
import com.quantum.holdup.domain.dto.UpdateReportDTO;
import com.quantum.holdup.domain.entity.Member;
import com.quantum.holdup.domain.entity.Report;
import com.quantum.holdup.domain.entity.ReportImage;
import com.quantum.holdup.domain.entity.ReviewImage;
import com.quantum.holdup.repository.MemberRepository;
import com.quantum.holdup.repository.ReportImageRepository;
import com.quantum.holdup.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository repo;
    private final MemberRepository memberRepo;
    private final ReportImageRepository reportImageRepo;
    private final S3Service s3Service;

    public Page<ReportDTO> findAllReport(Pageable pageable) {

        // 페이지 번호 조정 (0보다 크면 1을 빼고) 및 정렬 설정
        pageable = PageRequest.of(
                pageable.getPageNumber() <= 0 ? 0 : pageable.getPageNumber() - 1,
                pageable.getPageSize(),
                Sort.by("id").descending()
        );

        // 레파지토리의 findAll 메소드를 사용하여 Report 엔티티의 페이지를 가져옴
        Page<Report> reportsEntityList = repo.findAll(pageable);

        // 가져온 페이지를 바탕으로 페이징 버튼 정보 생성
        PagingButtonInfo paging = Pagination.getPagingButtonInfo(reportsEntityList);

        // Page<Report>를 Page<ReportDTO>로 변환하고 페이징 정보 추가
        return reportsEntityList.map(reportEntity -> {
            // 각 Report 엔티티로부터 새로운 ReportDTO 생성
            ReportDTO reportDTO = new ReportDTO(
                    reportEntity.getId(),
                    reportEntity.getTitle(),
                    reportEntity.getContent(),
                    reportEntity.getMember().getNickname(),
                    reportEntity.getCreateDate()
            );

            // 각 ReportDTO에 페이징 정보 설정
            reportDTO.setPagingInfo(paging);
            return reportDTO;
        });

    }

    public ReportDetailDTO findReportById(long id) {

        Report postEntity = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Post not found with id " + id));

        // 해당 리뷰의 이미지들 찾기
        List<ReportImage> reportImages = reportImageRepo.findByReportId(id);

        if (reportImages.isEmpty()) {
            throw new NoSuchElementException("No images found for review with id " + id);
        }

        List<String> imageUrls = reportImageRepo.findByReportId(id)
                .stream()
                .map(ReportImage::getImageUrl)
                .toList();

        List<Long> imageIds = reportImages
                .stream()
                .map(ReportImage::getId)
                .toList();

        return ReportDetailDTO.builder()
                .id(postEntity.getId())
                .title(postEntity.getTitle())
                .content(postEntity.getContent())
                .createDate(postEntity.getCreateDate())
                .nickname(postEntity.getMember().getNickname())
                .imageUrl(imageUrls)
                .imageId(imageIds)
                .build();
    }

//    public Page<ReportDTO> searchByNickname(String nickname, Pageable pageable) {
//
//        Page<Report> reportsEntityList = repo.findByMemberNickname(nickname, pageable);
//        PagingButtonInfo paging = Pagination.getPagingButtonInfo(reportsEntityList);
//
//        return reportsEntityList.map(reportEntity -> {
//            // 각 Report 엔티티로부터 새로운 ReportDTO 생성
//            ReportDTO reportDTO = new ReportDTO(
//                    reportEntity.getId(),
//                    reportEntity.getTitle(),
//                    reportEntity.getContent(),
//                    reportEntity.getMember().getNickname(),
//                    reportEntity.getCreateDate()
//            );
//
//            // 각 ReportDTO에 페이징 정보 설정
//            reportDTO.setPagingInfo(paging);
//            return reportDTO;
//        });
//
//    }

    public Object createReport(CreateReportDTO reportInfo, List<String> imageUrls) {

        // 로그인 되어있는 사용자의 이메일 가져옴
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // 가져온 이메일로 사용자 찾기
        Member member = memberRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Report report = Report.builder()
                .title(reportInfo.getTitle())
                .content(reportInfo.getContent())
                .member(member)
                .build();

        Report savedReport =  repo.save(report);

        if (imageUrls != null && !imageUrls.isEmpty()) {
            List<ReportImage> images = imageUrls.stream().map(url -> ReportImage.builder()
                    .imageUrl(url)
                    .imageName(extractFileNameFromUrl(url))
                    .report(savedReport)
                    .build()).toList();

            reportImageRepo.saveAll(images);
        }


        return CreateReportDTO.builder()
                .title(report.getTitle())
                .content(report.getContent())
                .build();
    }

    // url에서 이미지 name 추출
    private String extractFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    // 신고 게시글 수정
    public UpdateReportDTO updateReport(Long id, UpdateReportDTO modifyInfo,
                                        List<String> newImageUrls,
                                        List<Long> deleteImageId) {

        // 로그인 되어있는 사용자의 이메일 가져옴
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // 가져온 이메일로 사용자 찾기
        Member member = memberRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Report reportEntity = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Post not found with postId " + id));

        // toBuilder()를 사용하여 기존 객체를 기반으로 새 객체 생성
        Report updatedReport = reportEntity.toBuilder()
                .id(id)
                .member(member)
                .title(modifyInfo.getTitle())
                .content(modifyInfo.getContent())
                .createDate(reportEntity.getCreateDate())
                .build();

        // 새로운 엔티티 저장
        Report savedReport = repo.save(updatedReport);

        // 기존 이미지를 삭제할 경우
        if (deleteImageId != null && !deleteImageId.isEmpty()) {

            List<ReportImage> imagesToDelete = reportImageRepo.findAllById(deleteImageId);

            for (ReportImage image : imagesToDelete) {
                if (deleteImage(image.getId())) {
                    System.out.println("이미지 삭제 성공: " + image.getId());
                } else {
                    System.out.println("이미지 삭제 실패: " + image.getId());
                }
            }
        }

        // 새 이미지 추가
        if (newImageUrls != null && !newImageUrls.isEmpty()) {
            List<ReportImage> images = newImageUrls.stream().map(url -> ReportImage.builder()
                    .imageUrl((url))
                    .imageName(extractFileNameFromUrl((url)))
                    .report(savedReport)
                    .build()).toList();

            reportImageRepo.saveAll(images);
        }

        // ReportDTO 생성 및 반환
        return UpdateReportDTO.builder()
                .title(savedReport.getTitle())
                .content(savedReport.getContent())
                .build();
    }

    public boolean deleteImage(Long imageId) {
        try {
            // 1. DB에서 이미지 정보 조회
            ReportImage image = reportImageRepo.findById(imageId)
                    .orElseThrow(() -> new RuntimeException("Image not found with id: " + imageId));

            // 2. S3에서 이미지 파일 삭제
            String fileName = image.getImageName(); // S3에 저장된 파일 이름
            s3Service.deleteImage(fileName);

            // 3. DB에서 이미지 정보 삭제
            reportImageRepo.delete(image);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteReport(long id) {
        try {
            if (repo.existsById(id)) {
                repo.deleteById(id);
                return true; // 게시글 삭제 성공
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
