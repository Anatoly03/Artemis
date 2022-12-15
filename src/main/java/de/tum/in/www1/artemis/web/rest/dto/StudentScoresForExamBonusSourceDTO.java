package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

public class StudentScoresForExamBonusSourceDTO extends StudentScoresDTO {
    private long studentId;

    private boolean presentationScorePassed;

    private PlagiarismVerdict mostSeverePlagiarismVerdict;

    public StudentScoresForExamBonusSourceDTO(double absoluteScore, double relativeScore, double currentRelativeScore, int presentationScore, long studentId, boolean presentationScorePassed, PlagiarismVerdict mostSeverePlagiarismVerdict) {
        super(absoluteScore, relativeScore, currentRelativeScore, presentationScore);
        this.studentId = studentId;
        this.presentationScorePassed = presentationScorePassed;
        this.mostSeverePlagiarismVerdict = mostSeverePlagiarismVerdict;
    }

    public double getAbsolutePointsEligibleForBonus() {
        return presentationScorePassed ? this.absoluteScore : 0.0;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public boolean isPresentationScorePassed() {
        return presentationScorePassed;
    }

    public void setPresentationScorePassed(boolean presentationScorePassed) {
        this.presentationScorePassed = presentationScorePassed;
    }

    public PlagiarismVerdict getMostSeverePlagiarismVerdict() {
        return mostSeverePlagiarismVerdict;
    }

    public void setMostSeverePlagiarismVerdict(PlagiarismVerdict mostSeverePlagiarismVerdict) {
        this.mostSeverePlagiarismVerdict = mostSeverePlagiarismVerdict;
    }
}