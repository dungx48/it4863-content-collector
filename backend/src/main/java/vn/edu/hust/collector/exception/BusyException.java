package vn.edu.hust.collector.exception;

public class BusyException extends RuntimeException {
    public BusyException() { super("Đang xử lý yêu cầu khác; vui lòng thử lại."); }
}
