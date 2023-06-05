package com.pdfcampus.pdfcampus.service;

import com.pdfcampus.pdfcampus.dto.BookDto;
import com.pdfcampus.pdfcampus.dto.MynoteAssignDto;
import com.pdfcampus.pdfcampus.dto.MynoteDto;
import com.pdfcampus.pdfcampus.dto.SignupDto;
import com.pdfcampus.pdfcampus.entity.Mylib;
import com.pdfcampus.pdfcampus.entity.Note;
import com.pdfcampus.pdfcampus.entity.Sale;
import com.pdfcampus.pdfcampus.entity.User;
import com.pdfcampus.pdfcampus.repository.DetailNoteRepository;
import com.pdfcampus.pdfcampus.repository.MylibRepository;
import com.pdfcampus.pdfcampus.repository.MynoteRepository;
import com.pdfcampus.pdfcampus.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.amazonaws.services.s3.model.DeleteObjectRequest;

@Service
@Transactional
public class MynoteService {

    private final MynoteRepository mynoteRepository;
    private final MylibRepository mylibRepository;
    private final SaleRepository saleRepository;
    private final ReadBookService readBookService;
    private final DetailNoteRepository detailNoteRepository;

    @Autowired
    public MynoteService(MynoteRepository mynoteRepository, SaleRepository saleRepository, MylibRepository mylibRepository, ReadBookService readBookService, DetailNoteRepository detailNoteRepository) {
        this.mynoteRepository = mynoteRepository;
        this.saleRepository = saleRepository;
        this.mylibRepository = mylibRepository;
        this.readBookService = readBookService;
        this.detailNoteRepository = detailNoteRepository;
    }

    public boolean deleteNote(String uid, String nid) {
        Integer userId = Integer.parseInt(uid);
        Integer noteId = Integer.parseInt(nid);
        Optional<Mylib> dataOptional = mylibRepository.findByUidAndNid(userId, noteId);

        if (dataOptional.isPresent()) {
            mynoteRepository.deleteByNid(noteId);
            return true;
        }

        return false;
    }

    public boolean existsByNid(String noteId) {
        Integer nidInt = Integer.parseInt(noteId);
        return saleRepository.existsByNoteNid(nidInt);
    }

    public Sale assignNote(MynoteAssignDto mynoteAssignDto) {
        Integer nidInt = Integer.parseInt(mynoteAssignDto.getNoteId());
        Note note = detailNoteRepository.findByNid(nidInt)
                .orElseThrow(() -> new EntityNotFoundException("note not found with id " + mynoteAssignDto.getNoteId()));

        Sale sale = mynoteAssignDto.toEntity(note, mynoteAssignDto.getPrice());

        return saleRepository.save(sale);
    }

    public List<MynoteDto> getMynoteByUserId(String userId) {
        List<Note> noteList = mynoteRepository.findByUserUid(Integer.valueOf(userId));

        if (noteList == null || noteList.isEmpty()) {
            return null;
        }

        List<MynoteDto> mynoteDtoList = new ArrayList<>();

        for (Note note : noteList) {
            boolean isSale = saleRepository.existsByNoteNid(note.getNid());

            MynoteDto mynoteDto = new MynoteDto();
            mynoteDto.setNoteId(note.getNid());
            mynoteDto.setNoteTitle(note.getNoteTitle());
            mynoteDto.setCreatedAt(note.getCreatedAt());
            mynoteDto.setModifiedAt(note.getModifiedAt());
            mynoteDto.setIsSale(isSale);

            String bookCoverUrl = null;
            try {
                bookCoverUrl = readBookService.getBookCoverUrl(String.valueOf(note.getBook().getBid())).toString();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }

            BookDto bookDto = new BookDto();
            bookDto.setAuthor(note.getBook().getAuthor());
            bookDto.setPublisher(note.getBook().getPublisher());
            bookDto.setPublicationYear(note.getBook().getPublicationYear());
            bookDto.setBookCover(bookCoverUrl);
            mynoteDto.setBookInfo(bookDto);

            mynoteDtoList.add(mynoteDto);
        }

        return mynoteDtoList;
    }
}
