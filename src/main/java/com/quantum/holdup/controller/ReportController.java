package com.quantum.holdup.controller;

import com.quantum.holdup.domain.dto.CreateReportDTO;
import com.quantum.holdup.domain.dto.MemberDTO;
import com.quantum.holdup.domain.dto.ReportDTO;
import com.quantum.holdup.domain.dto.UpdateReportDTO;
import com.quantum.holdup.domain.entity.Member;
import com.quantum.holdup.message.ResponseMessage;
import com.quantum.holdup.service.MemberService;
import com.quantum.holdup.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;
    private final MemberService memberService;

    // 신고글 전체조회
    @GetMapping("/report")
    public ResponseEntity<ResponseMessage> findAllReport(@PageableDefault Pageable pageable, String nickname,
                                                         @RequestParam(value = "searchType", required = false) String searchType){

        Page<ReportDTO> reports;

        MemberDTO member = new MemberDTO();

        if (member.getNickname() != null && !nickname.isEmpty() && "nickname".equals(searchType)) {
            reports = service.searchByMemberId(nickname, pageable);
        } else {
            reports = service.findAllReport(pageable);
        }

        return ResponseEntity.ok()
                .body(new ResponseMessage(
                        "신고 전체조회를 성공했습니다.",
                        reports
                ));
    }

    // 신고글 추가
    @PostMapping("/report")
    public ResponseEntity<?> createReport(@RequestBody CreateReportDTO reportInfo) {
        return ResponseEntity.ok()
                .body(new ResponseMessage(
                        "게시판 등록에 성공하였습니다.",
                        service.createReport(reportInfo)
                ));
    }

    // 신고글 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<ResponseMessage> findReportById(@PathVariable long id) {

        ReportDTO reports = service.findReportById(id);

        return ResponseEntity.ok()
                .body(new ResponseMessage(
                        "아이디로 게시글 조회 성공"
                        , reports)
                );
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> modifyPost(@PathVariable Long id, @RequestBody UpdateReportDTO modifyInfo) {

        ReportDTO updatedPost = service.updateReport(id, modifyInfo);

        return ResponseEntity.ok()
                .body(new ResponseMessage(
                        "게시글 수정 완료",
                        updatedPost)
                );
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePost(@PathVariable long id) {

        Map<String, Object> responseMap = new HashMap<>();

        boolean isDeleted = service.deleteReport(id);

        String msg;

        if (isDeleted) {
            msg = "게시글 삭제에 성공하였습니다.";
        } else {
            msg = "게시글 삭제에 실패하였습니다.";
        }
        responseMap.put("result", msg);

        return ResponseEntity
                .ok()
                .body(new ResponseMessage(
                        "게시글 삭제 성공",
                        responseMap)
                );
    }
}
