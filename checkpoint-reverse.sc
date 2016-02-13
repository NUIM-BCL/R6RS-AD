;;;\needswork: concrete->abstract syntax, constant conversion, basis, letrec
;;;\needswork: We don't provide (co)tangent vector mode.
;;;\needswork: We don't provide the ability to choose y-sensitivity after
;;             computing y.
;;;\needswork: We don't provide the ability to perform the (co)tangent
;;;            computation multiple times after a single primal computation.

(define-record-type il:variable-access-expression (fields variable))
(define-record-type il:lambda-expression (fields variable expression))
(define-record-type il:unary-expression (fields procedure expression))
(define-record-type il:binary-expression
 (fields procedure expression1 expression2))
(define-record-type il:ternary-expression
 (fields procedure expression1 expression2 expression))
(define-record-type il:binding (fields variable value))
(define-record-type il:closure (fields expression environment))
(define-record-type il:continuation (fields procedure values))
(define-record-type il:checkpoint
 (fields continuation expression environment count))

;;; Short-circuit if is implemented with syntax that wraps with lambda and a
;;; ternary expression that implements applicative-order if.
;;; Application is implemented as a binary expression with il:apply as the
;;; procedure.
;;; j* and *j are implemented as ternary expressions with il:j* and il:*j as the
;;; procedure.

(define (il:lookup variable environment)
 (cond ((null? environment) (error #f "Unbound variable"))
       ((eq? variable (il:binding-variable (first environment)))
	(il:binding-value (first environment)))
       (else (il:lookup variable (rest environment)))))

(define (il:apply value1 value2)
 (if (il:closure? value1)
     (il:eval
      (il:lambda-expression-expression (il:closure-expression value1))
      (cons (make-il:binding
	     (il:lambda-expression-variable (il:closure-expression value1))
	     value2)
	    (il:closure-environment value1)))
     (error #f "Not a closure")))

(define (il:walk1 f x)
 (cond ((eq? x #t) #t)
       ((eq? x #f) #f)
       ((null? x) '())
       ((real? x) (f x))
       ((pair? x) (cons (il:walk1 f (car x)) (il:walk1 f (cdr x))))
       ((il:binding? x)
	(make-il:binding (il:binding-variable x)
			 (il:walk1 f (il:binding-value x))))
       ((il:closure? x)
	(make-il:closure (il:closure-expression x)
			 (il:walk1 f (il:closure-environment x))))
       ((il:continuation? x)
	(make-il:continuation (il:continuation-procedure x)
			      (il:walk1 f (il:continuation-values x))))
       ((il:checkpoint? x)
	(make-il:checkpoint (il:walk1 f (il:checkpoint-continuation x))
			    (il:checkpoint-expression x)
			    (il:walk1 f (il:checkpoint-environment x))
			    (il:checkpoint-count x)))
       (else (error #f "Can't walk1"))))

(define (il:walk2 f x x-prime)
 (cond
  ((and (eq? x #t) (eq? x-prime #t)) #t)
  ((and (eq? x #f) (eq? x-prime #f)) #f)
  ((and (null? x) (null? x-prime)) '())
  ((and (real? x) (real? x-prime)) (f x x-prime))
  ((and (pair? x) (pair? x-prime))
   (cons (il:walk2 f (car x) (car x-prime))
	 (il:walk2 f (cdr x) (cdr x-prime))))
  ((and (il:binding? x)
	(il:binding? x-prime)
	(eq? (il:binding-variable x) (il:binding-variable x-prime)))
   (make-il:binding
    (il:binding-variable x)
    (il:walk2 f (il:binding-value x) (il:binding-value x-prime))))
  ((and (il:closure? x) (il:closure? x-prime)
	(eq? (il:closure-expression x) (il:closure-expression x-prime)))
   (make-il:closure (il:closure-expression x)
		    (il:walk2 f
			      (il:closure-environment x)
			      (il:closure-environment x-prime))))
  ((and (il:continuation? x)
	(il:continuation? x-prime)
	(eq? (il:continuation-procedure x) (il:continuation-procedure x-prime)))
   (make-il:continuation
    (il:continuation-procedure x)
    (il:walk2 f (il:continuation-values x) (il:continuation-values x-prime))))
  ((and (il:checkpoint? x)
	(il:checkpoint? x-prime)
	(eq? (il:checkpoint-expression x) (il:checkpoint-expression x-prime))
	(= (il:checkpoint-count x) (il:checkpoint-count x-prime)))
   (make-il:checkpoint (il:walk2 f
				 (il:checkpoint-continuation x)
				 (il:checkpoint-continuation x-prime))
		       (il:checkpoint-expression x)
		       (il:walk2 f
				 (il:checkpoint-environment x)
				 (il:checkpoint-environment x-prime))
		       (il:checkpoint-count x)))
  (else (error #f "Can't walk2"))))

(define (il:walk1! f x)
 (cond ((eq? x #t) #f)
       ((eq? x #f) #f)
       ((null? x) #f)
       ((real? x) (f x))
       ((pair? x) (il:walk1! f (car x)) (il:walk1! f (cdr x)))
       ((il:binding? x) (il:walk1! f (il:binding-value x)))
       ((il:closure? x) (il:walk1! f (il:closure-environment x)))
       ((il:continuation? x) (il:walk1! f (il:continuation-values x)))
       ((il:checkpoint? x)
	(il:walk1! f (il:checkpoint-continuation x))
	(il:walk1! f (il:checkpoint-environment x)))
       (else (error #f "Can't walk1!"))))

(define (il:walk2! f x x-prime)
 (cond
  ((and (eq? x #t) (eq? x-prime #t)) #f)
  ((and (eq? x #f) (eq? x-prime #f)) #f)
  ((and (null? x) (null? x-prime)) #f)
  ((and (real? x) (real? x-prime)) (f x x-prime))
  ((and (pair? x) (pair? x-prime))
   (il:walk2! f (car x) (car x-prime))
   (il:walk2! f (cdr x) (cdr x-prime)))
  ((and (il:binding? x)
	(il:binding? x-prime)
	(eq? (il:binding-variable x) (il:binding-variable x-prime)))
   (il:walk2! f (il:binding-value x) (il:binding-value x-prime)))
  ((and (il:closure? x)
	(il:closure? x-prime)
	(eq? (il:closure-expression x) (il:closure-expression x-prime)))
   (il:walk2! f (il:closure-environment x) (il:closure-environment x-prime)))
  ((and (il:continuation? x)
	(il:continuation? x-prime)
	(eq? (il:continuation-procedure x) (il:continuation-procedure x-prime)))
   (il:walk2! f (il:continuation-values x) (il:continuation-values x-prime)))
  ((and (il:checkpoint? x)
	(il:checkpoint? x-prime)
	(eq? (il:checkpoint-expression x) (il:checkpoint-expression x-prime))
	(= (il:checkpoint-count x) (il:checkpoint-count x-prime)))
   (il:walk2!
    f (il:checkpoint-continuation x) (il:checkpoint-continuation x-prime))
   (il:walk2!
    f (il:checkpoint-environment x) (il:checkpoint-environment x-prime)))
  (else (error #f "Can't walk2!"))))

(define (il:j* value1 value2 value3)
 (forward-mode il:walk2
	       il:walk1
	       (lambda (x) (il:apply value1 x))
	       value2
	       value3))

(define (il:*j value1 value2 value3)
 (let ((y (reverse-mode il:walk1
			il:walk1
			il:walk1!
			il:walk2!
			(lambda (x) (il:apply value1 x))
			value2
			(list value3))))
  (list (car y) (car (cadr y)))))

(define (il:eval expression environment)
 (cond
  ((il:variable-access-expression? expression)
   (il:lookup (il:variable-access-expression-variable expression) environment))
  ((il:lambda-expression? expression)
   (make-il:closure expression environment))
  ((il:unary-expression? expression)
   ((il:unary-expression-procedure expression)
    (il:eval (il:unary-expression-expression expression) environment)))
  ((il:binary-expression? expression)
   ((il:binary-expression-procedure expression)
    (il:eval (il:binary-expression-expression1 expression) environment)
    (il:eval (il:binary-expression-expression2 expression) environment)))
  ((il:ternary-expression? expression)
   ((il:ternary-expression-procedure expression)
    (il:eval (il:ternary-expression-expression1 expression) environment)
    (il:eval (il:ternary-expression-expression2 expression) environment)
    (il:eval (il:ternary-expression-expression3 expression) environment)))
  (else (error #f "Invalid expression"))))

;;; convert to CPS

;;;\needswork: There may be a bug here and in the original AD.ss as to where
;;; (set! *e* (- *e* 1)) belongs in forward-mode and reverse-mode.

(define (forward-mode
	 continuation map-independent map-dependent f x x-perturbation)
 (set! *e* (+ *e* 1))
 (f (lambda (y-forward)
     (set! *e* (- *e* 1))
     (continuation
      (list (map-dependent (lambda (y-forward)
			    (if (or (not (dual-number? y-forward))
				    (<_e (dual-number-epsilon y-forward) *e*))
				y-forward
				(dual-number-primal y-forward)))
			   y-forward)
	    (map-dependent (lambda (y-forward)
			    (if (or (not (dual-number? y-forward))
				    (<_e (dual-number-epsilon y-forward) *e*))
				0
				(dual-number-perturbation y-forward)))
			   y-forward))))
    (map-independent (lambda (x x-perturbation)
		      (make-dual-number *e* x x-perturbation))
		     x
		     x-perturbation)))

(define (reverse-mode continuation
		      map-independent
		      map-dependent
		      for-each-dependent1!
		      for-each-dependent2!
		      f
		      x
		      y-sensitivities)
 (set! *e* (+ *e* 1))
 (let ((x-reverse (map-independent tapify x)))
  (f (lambda (y-reverse)
      (let ((x-sensitivities
	     (map (lambda (y-sensitivity)
		   (for-each-dependent1!
		    (lambda (y-reverse)
		     (when (and (tape? y-reverse)
				(not (<_e (tape-epsilon y-reverse) *e*)))
		      (determine-fanout! y-reverse)
		      (initialize-sensitivity! y-reverse)))
		    y-reverse)
		   (for-each-dependent2!
		    (lambda (y-reverse y-sensitivity)
		     (when (and (tape? y-reverse)
				(not (<_e (tape-epsilon y-reverse) *e*)))
		      (determine-fanout! y-reverse)
		      (reverse-phase! y-sensitivity y-reverse)))
		    y-reverse
		    y-sensitivity)
		   (map-independent tape-sensitivity x-reverse))
		  y-sensitivities)))
       (set! *e* (- *e* 1))
       (continuation
	(list
	 (map-dependent
	  (lambda (y-reverse)
	   (if (or (not (tape? y-reverse)) (<_e (tape-epsilon y-reverse) *e*))
	       y-reverse
	       (tape-primal y-reverse)))
	  y-reverse)
	 x-sensitivities))))
     x-reverse)))

(define (il:apply continuation value1 value2)
 (if (il:closure? value1)
     (il:eval
      continuation
      (il:lambda-expression-expression (il:closure-expression value1))
      (cons (make-il:binding
	     (il:lambda-expression-variable (il:closure-expression value1))
	     value2)
	    (il:closure-environment value1)))
     (error #f "Not a closure")))

(define (il:j* continuation value1 value2 value3)
 (forward-mode continuation
	       il:walk2
	       il:walk1
	       (lambda (continuation x) (il:apply continuation value1 x))
	       value2
	       value3))

(define (il:*j continuation value1 value2 value3)
 (reverse-mode (lambda (y) (continuation (list (car y) (car (cadr y)))))
	       il:walk1
	       il:walk1
	       il:walk1!
	       il:walk2!
	       (lambda (continuation x) (il:apply continuation value1 x))
	       value2
	       (list value3)))

(define (il:eval continuation expression environment)
 (cond
  ((il:variable-access-expression? expression)
   (continuation
    (il:lookup
     (il:variable-access-expression-variable expression) environment)))
  ((il:lambda-expression? expression)
   (continuation (make-il:closure expression environment)))
  ((il:unary-expression? expression)
   (il:eval
    (lambda (value)
     ((il:unary-expression-procedure expression) continuation value))
    (il:unary-expression-expression expression)
    environment))
  ((il:binary-expression? expression)
   (il:eval
    (lambda (value1)
     (il:eval
      (lambda (value2)
       ((il:binary-expression-procedure expression) continuation value1 value2))
      (il:binary-expression-expression2 expression)
      environment))
    (il:binary-expression-expression1 expression)
    environment))
  ((il:ternary-expression? expression)
   (il:eval (lambda (value1)
	     (il:eval (lambda (value2)
		       (il:eval (lambda (value3)
				 ((il:ternary-expression-procedure expression)
				  continuation value1 value2 value3))
				(il:ternary-expression-expression3 expression)
				environment))
		      (il:ternary-expression-expression2 expression)
		      environment))
	    (il:ternary-expression-expression1 expression)
	    environment))
  (else (error #f "Invalid expression"))))

;;; closure convert

(define (il:make-continuation procedure . values)
 (make-il:continuation procedure values))

;;; The il:unary/binary/ternary-expression-procedures must call their
;;; continuation with il:call-continuation. il:apply already does so
;;; through its call to il:eval. il:j* and il:*j are modified to do so below.

(define (il:call-continuation continuation value)
 (apply (il:continuation-procedure continuation)
	value
	(il:continuation-values continuation)))

(define (forward-mode
	 continuation map-independent map-dependent f x x-perturbation)
 (set! *e* (+ *e* 1))
 ;; This does not need to close over continuation or map-dependent since they
 ;; are opaque.
 (f (il:make-continuation
     (lambda (y-forward)
      (set! *e* (- *e* 1))
      (il:call-continuation
       continuation
       (list (map-dependent (lambda (y-forward)
			     (if (or (not (dual-number? y-forward))
				     (<_e (dual-number-epsilon y-forward) *e*))
				 y-forward
				 (dual-number-primal y-forward)))
			    y-forward)
	     (map-dependent (lambda (y-forward)
			     (if (or (not (dual-number? y-forward))
				     (<_e (dual-number-epsilon y-forward) *e*))
				 0
				 (dual-number-perturbation y-forward)))
			    y-forward)))))
    (map-independent (lambda (x x-perturbation)
		      (make-dual-number *e* x x-perturbation))
		     x
		     x-perturbation)))

(define (reverse-mode continuation
		      map-independent
		      map-dependent
		      for-each-dependent1!
		      for-each-dependent2!
		      f
		      x
		      y-sensitivities)
 (set! *e* (+ *e* 1))
 (let ((x-reverse (map-independent tapify x)))
  ;; This does not need to close over continuation, map-independent,
  ;; map-dependent,  for-each-dependent1!, or for-each-dependent2! since they
  ;; are opaque.
  (f (il:make-continuation
      (lambda (y-reverse x-reverse y-sensitivities)
       (let ((x-sensitivities
	      (map (lambda (y-sensitivity)
		    (for-each-dependent1!
		     (lambda (y-reverse)
		      (when (and (tape? y-reverse)
				 (not (<_e (tape-epsilon y-reverse) *e*)))
		       (determine-fanout! y-reverse)
		       (initialize-sensitivity! y-reverse)))
		     y-reverse)
		    (for-each-dependent2!
		     (lambda (y-reverse y-sensitivity)
		      (when (and (tape? y-reverse)
				 (not (<_e (tape-epsilon y-reverse) *e*)))
		       (determine-fanout! y-reverse)
		       (reverse-phase! y-sensitivity y-reverse)))
		     y-reverse
		     y-sensitivity)
		    (map-independent tape-sensitivity x-reverse))
		   y-sensitivities)))
	(set! *e* (- *e* 1))
	(il:call-continuation
	 continuation
	 (list
	  (map-dependent
	   (lambda (y-reverse)
	    (if (or (not (tape? y-reverse)) (<_e (tape-epsilon y-reverse) *e*))
		y-reverse
		(tape-primal y-reverse)))
	   y-reverse)
	  x-sensitivities))))
      x-reverse
      y-sensitivities)
     x-reverse)))

(define (il:j* continuation value1 value2 value3)
 (forward-mode continuation
	       il:walk2
	       il:walk1
	       (lambda (continuation x) (il:apply continuation value1 x))
	       value2
	       value3))

(define (il:*j continuation value1 value2 value3)
 (reverse-mode
  (il:make-continuation
   (lambda (y continuation)
    (il:call-continuation continuation (list (car y) (car (cadr y)))))
   continuaton)
  il:walk1
  il:walk1
  il:walk1!
  il:walk2!
  (lambda (continuation x) (il:apply continuation value1 x))
  value2
  (list value3)))

(define (il:eval continuation expression environment)
 (cond
  ((il:variable-access-expression? expression)
   (il:call-continuation
    continuation
    (il:lookup
     (il:variable-access-expression-variable expression) environment)))
  ((il:lambda-expression? expression)
   (il:call-continuation
    continuation (make-il:closure expression environment)))
  ((il:unary-expression? expression)
   (il:eval (il:make-continuation
	     (lambda (value continuation)
	      ((il:unary-expression-procedure expression) continuation value))
	     continuation)
	    (il:unary-expression-expression expression)
	    environment))
  ((il:binary-expression? expression)
   (il:eval
    (il:make-continuation
     (lambda (value1 continuation)
      (il:eval (il:make-continuation
		(lambda (value2 continuation value1)
		 ((il:binary-expression-procedure expression)
		  continuation value1 value2))
		continuation
		value1)
	       (il:binary-expression-expression2 expression)
	       environment))
     continuation)
    (il:binary-expression-expression1 expression) environment))
  ((il:ternary-expression? expression)
   (il:eval
    (il:make-continuation
     (lambda (value1 continuation)
      (il:eval (il:make-continuation
		(lambda (value2 continuation value1)
		 (il:eval (il:make-continuation
			   (lambda (value3 continuation value1 value2)
			    ((il:ternary-expression-procedure expression)
			     continuation value1 value2 value3))
			   continuation
			   value1
			   value2)
			  (il:ternary-expression-expression3 expression)
			  environment))
		continuation
		value1)
	       (il:ternary-expression-expression2 expression)
	       environment))
     continuation)
    (il:ternary-expression-expression1 expression)
    environment))
  (else (error #f "Invalid expression"))))

;;; thread count, increment count, add limit, add checkpointing

;;; The signature of the il:unary/binary/ternary-expression-procedures must
;;; be modified to take count and limit as arguments and call their
;;; continuation with count and limit. il:apply, il:j*, and il:*j are modified
;;; to do so below.

(define (il:call-continuation continuation value count)
 (apply (il:continuation-procedure continuation)
	value
	count
	(il:continuation-values continuation)))

(define (forward-mode
	 continuation map-independent map-dependent f x x-perturbation)
 (set! *e* (+ *e* 1))
 ;; This does not need to close over continuation or map-dependent since they
 ;; are opaque.
 (f (il:make-continuation
     (lambda (y-forward count)
      (set! *e* (- *e* 1))
      (il:call-continuation
       continuation
       (list (map-dependent (lambda (y-forward)
			     (if (or (not (dual-number? y-forward))
				     (<_e (dual-number-epsilon y-forward) *e*))
				 y-forward
				 (dual-number-primal y-forward)))
			    y-forward)
	     (map-dependent (lambda (y-forward)
			     (if (or (not (dual-number? y-forward))
				     (<_e (dual-number-epsilon y-forward) *e*))
				 0
				 (dual-number-perturbation y-forward)))
			    y-forward))
       count)))
    (map-independent (lambda (x x-perturbation)
		      (make-dual-number *e* x x-perturbation))
		     x
		     x-perturbation)))

(define (reverse-mode continuation
		      map-independent
		      map-dependent
		      for-each-dependent1!
		      for-each-dependent2!
		      f
		      x
		      y-sensitivities)
 (set! *e* (+ *e* 1))
 (let ((x-reverse (map-independent tapify x)))
  ;; This does not need to close over continuation, map-independent,
  ;; map-dependent,  for-each-dependent1!, or for-each-dependent2! since they
  ;; are opaque.
  (f (il:make-continuation
      (lambda (y-reverse count x-reverse y-sensitivities)
       (let ((x-sensitivities
	      (map (lambda (y-sensitivity)
		    (for-each-dependent1!
		     (lambda (y-reverse)
		      (when (and (tape? y-reverse)
				 (not (<_e (tape-epsilon y-reverse) *e*)))
		       (determine-fanout! y-reverse)
		       (initialize-sensitivity! y-reverse)))
		     y-reverse)
		    (for-each-dependent2!
		     (lambda (y-reverse y-sensitivity)
		      (when (and (tape? y-reverse)
				 (not (<_e (tape-epsilon y-reverse) *e*)))
		       (determine-fanout! y-reverse)
		       (reverse-phase! y-sensitivity y-reverse)))
		     y-reverse
		     y-sensitivity)
		    (map-independent tape-sensitivity x-reverse))
		   y-sensitivities)))
	(set! *e* (- *e* 1))
	(il:call-continuation
	 continuation
	 (list
	  (map-dependent
	   (lambda (y-reverse)
	    (if (or (not (tape? y-reverse)) (<_e (tape-epsilon y-reverse) *e*))
		y-reverse
		(tape-primal y-reverse)))
	   y-reverse)
	  x-sensitivities)
	 count)))
      x-reverse
      y-sensitivities)
     x-reverse)))

(define (il:apply continuation value1 value2 count limit)
 (if (il:closure? value1)
     (il:eval
      continuation
      (il:lambda-expression-expression (il:closure-expression value1))
      (cons (make-il:binding
	     (il:lambda-expression-variable (il:closure-expression value1))
	     value2)
	    (il:closure-environment value1))
      count
      limit)
     (error #f "Not a closure")))

(define (il:j* continuation value1 value2 value3 count limit)
 (forward-mode
  continuation
  il:walk2
  il:walk1
  (lambda (continuation x) (il:apply continuation value1 x count limit))
  value2
  value3))

(define (il:*j continuation value1 value2 value3 count limit)
 (reverse-mode
  (il:make-continuation
   (lambda (y count continuation)
    (il:call-continuation continuation (list (car y) (car (cadr y))) count))
   continuaton)
  il:walk1
  il:walk1
  il:walk1!
  il:walk2!
  (lambda (continuation x) (il:apply continuation value1 x count limit))
  value2
  (list value3)))

(define (il:eval continuation expression environment count limit)
 ;; Every entry into il:eval increments count exactly once. So if you call
 ;; il:eval with count=0 and it doesn't checkpoint, continuation is called with
 ;; count=n being the total number of calls to (entries into) il:eval during the
 ;; evaluation. If you evaluate the same expression in the same environment
 ;; with count=0 and limit>=n then it will not checkpoint. But if called with
 ;; count=0 and limit=(0<=i<n) then it will checkpoint upon entry to the ith
 ;; call. The entries are numbered 0,...,i.
 (if (= count limit)
     (make-il:checkpoint continuation expression environment count)
     (cond
      ((il:variable-access-expression? expression)
       (il:call-continuation
	continuation
	(il:lookup (il:variable-access-expression-variable expression)
		   environment)
	(+ count 1)))
      ((il:lambda-expression? expression)
       (il:call-continuation continuation
			     (make-il:closure expression environment)
			     (+ count 1)))
      ((il:unary-expression? expression)
       (il:eval
	(il:make-continuation
	 (lambda (value count continuation)
	  ((il:unary-expression-procedure expression)
	   continuation value count limit))
	 continuation)
	(il:unary-expression-expression expression)
	environment
	(+ count 1)
	limit))
      ((il:binary-expression? expression)
       (il:eval
	(il:make-continuation
	 (lambda (value1 count continuation)
	  (il:eval
	   (il:make-continuation
	    (lambda (value2 count continuation value1)
	     ((il:binary-expression-procedure expression)
	      continuation value1 value2 count limit))
	    continuation
	    value1)
	   (il:binary-expression-expression2 expression)
	   environment
	   count
	   limit))
	 continuation)
	(il:binary-expression-expression1 expression)
	environment
	(+ count 1)
	limit))
      ((il:ternary-expression? expression)
       (il:eval
	(il:make-continuation
	 (lambda (value1 count continuation)
	  (il:eval
	   (il:make-continuation
	    (lambda (value2 count continuation value1)
	     (il:eval (il:make-continuation
		       (lambda (value3 count continuation value1 value2)
			((il:ternary-expression-procedure expression)
			 continuation value1 value2 value3 count limit))
		       continuation
		       value1
		       value2)
		      (il:ternary-expression-expression3 expression)
		      environment
		      count
		      limit))
	    continuation
	    value1)
	   (il:ternary-expression-expression2 expression)
	   environment
	   count
	   limit))
	 continuation)
	(il:ternary-expression-expression1 expression)
	environment
	(+ count 1)
	limit))
      (else (error #f "Invalid expression")))))

;;; Now we have the substrate to implement checkpointing reverse. It should
;;; have the same interface as il:*j.

(define (il:checkpoint-*j continuation value1 value2 value3 count limit)
 ;;\needswork: What happens when there is nesting? When there is no nesting,
 ;;            the input limit is infinity. So primops doesn't checkpoint. And
 ;;            resume doesn't checkpoint. But in a nested call, limit can be
 ;;            finite and short enough to cause primops and resume to
 ;;            checkpoint.
 ;; The first half of the computation is executed three times:
 ;; 1. (y,2n)=primops(f,x)
 ;; 2. c=checkpoint(f,x,n)
 ;; 4. (c,x`)=*j(\x.checkpoint(f,x,n),x,c`)
 ;; The second half of the computation is executed two times:
 ;; 1. (y,2n)=primops(f,x)
 ;; 3. (y,c`)=*j(\c.resume(c),c,y`)
 ;; But each half is counted only once.
 ;;
 ;; 1. (y,2n)=primops(f,x)
 ;; f is value1
 ;; x is value2
 ;; y is value4
 ;; n is (quotient (- count4 count) 2)
 (il:apply
  (il:make-continuation
   (lambda (value4 count4 continuation value1 value2 value3)
    ;; count4 must be greater than count because it is incremented before any
    ;; path to calling this continuation to il:apply.
    ;; (= (+ count 1) count4)<->(zero? (quotient (- count4 count) 2))
    (if (= (+ count 1) count4)
	;; This means that the count for computing primops is ignored.
	(il:*j continuation value1 value2 value3 count limit)
	;; 2. c=checkpoint(f,x,n)
	;; f is value1
	;; x is value2
	;; n is (quotient (- count4 count) 2)
	;; c is checkpoint5
	(let ((checkpoint5
	       (il:apply
		;; This continuation won't be called with the checkpoint
		;; computation but will be called upon resume.
		(il:make-continuation
		 ;; This closes over value4 and checkpoint5 only for consistency
		 ;; checking.
		 (lambda (value6 count6 continuation value1 value2 value4
				 checkpoint5)
		  ;; 4. (c,x`)=*j(\x.checkpoint(f,x,n),x,c`)
		  ;; f is value1
		  ;; x is value2
		  ;; c` is (second value6)
		  ;; c is (first value7)
		  ;; x` is (second value7)
		  (unless (equal? value4 (first value6))
		   (error #f "(not (equal? value4 (first value6)))"))
		  (il:checkpoint-*j
		   (il:make-continuation
		    ;; This closes over checkpoint5 only for consistency
		    ;; checking
		    (lambda (value7 count7 continuation checkpoint5 value6)
		     (unless (= count7 (+ count (quotient (- count4 count) 2)))
		      (error #f "(not (= count7 (+ count (quotient (- count4 count) 2))))"))
		     (unless (equal? checkpoint5 (first value7))
		      (error #f "(not (equal? checkpoint5 (first value7)))"))
		     (il:call-continuation
		      ;; This fakes the count as if both halves of the
		      ;; computation were executed exactly once.
		      continuation
		      (list (first value6) (second value7))
		      count4))
		    continuation
		    checkpoint5
		    value6)
		   ;; This is a closure that behaves like \x.checkpoint(f,x,n).
		   (make-il:closure
		    (make-il:lambda-expression
		     'x
		     (make-il:binary-expression
		      (lambda (continuation8 value8 value9 count8 limit8)
		       (unless (= count8 count)
			(error #f "(not (= count8 count))"))
		       (unless (= limit8
				  (+ count (quotient (- count4 count) 2)))
			(error #f "(not (= limit8 (+ count (quotient (- count4 count) 2))))"))
		       (il:apply continuation8 value8 value9 count8 limit8))
		      (make-il:variable-access-expression 'f)
		      (make-il:variable-access-expression 'x)))
		    (list (make-il:binding 'f value1)))
		   value2
		   (second value6)
		   ;; This is for the first half of the computation.
		   count
		   ;; If (zero? (quotient (- count4 count) 2)) then the
		   ;; evaluation could checkpoint right at the start without
		   ;; making any progress. But that can't happen.
		   (+ count (quotient (- count4 count) 2))))
		 continuation
		 value1
		 value2
		 value4
		 checkpoint5)
		value1
		value2
		;; This is for the first half of the computation.
		;; This means that the count for computing primops is ignored.
		count
		;; If (zero? (quotient (- count4 count) 2)) then the evaluation
		;; could checkpoint right at the start without making any
		;; progress. But that can't happen.
		(+ count (quotient (- count4 count) 2)))))
	 ;; 3. (y,c`)=*j(\c.resume(c),c,y`)
	 ;; c is checkpoint5
	 ;; y` is value3
	 ;; y is (first value6)
	 ;; c` is (second value6)
	 (il:checkpoint-*j
	  ;; This continuation will become continuation10 and never be called.
	  'continuation10
	  ;; This is a closure that behaves like \c.resume(c).
	  (make-il:closure
	   (make-il:lambda-expression
	    'c
	    (make-il:unary-expression
	     (lambda (continuation10 value10 count10 limit10)
	      ;; continuation10 is ignored. What this means is that the CPS
	      ;; resume never calls its continuation, which in turn means that
	      ;; the direction-style resume never returns. This is correct, and
	      ;; is analogous to fail never returning.
	      ;;\needswork: Could eliminate (il:checkpoint-count value10).
	      (unless (= count10 (il:checkpoint-count value10))
	       (error #f "(not (= count10 (il:checkpoint-count value10)))"))
	      (unless (= count10 (+ count (quotient (- count4 count) 2)))
	       (error
		#f "(not (= count10 (+ count (quotient (- count4 count) 2))))"))
	      (unless (= limit10 limit) (error #f "(not (= limit10 limit))"))
	      (il:eval (il:checkpoint-continuation value10)
		       (il:checkpoint-expression value10)
		       (il:checkpoint-environment value10)
		       count10
		       limit10))
	     (make-il:variable-access-expression 'c)))
	   '())
	  checkpoint5
	  value3
	  ;; This is for the second half of the computation.
	  ;;\needswork: To guarantee that
	  ;;            (> limit (+ count (quotient (- count4 count) 2))).
	  (+ count (quotient (- count4 count) 2))
	  limit))))
   continuation
   value1
   value2
   value3)
  value1
  value2
  count
  limit))
