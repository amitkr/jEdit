# Makefile
all:
	@make -C src
	@make -C plugins
install:
	@make -C src install
	@make -C plugins install
	@make -C doc install
	@make -C bin install
	@echo
	@echo "Type 'make kde' to install KDE applnks for jEdit and jOpen."
	@echo
kde:
	@(echo "Where is KDE located? [/opt/kde]";\
	read kdedir;\
	if [ "$$kdedir" = "" ];\
	then\
	kdedir=/opt/kde;\
	fi;\
	cp etc/*.kdelnk $$kdedir/share/applnk/Applications;)
clean:
	find -name \*~ -exec rm {} \;
	find -name .\*~ -exec rm {} \;
	find -name \*.bak -exec rm {} \;
	find -name \#\*\# -exec rm {} \;
	find -name .\*.swp -exec rm {} \;
	find -name \*.class -exec rm {} \;
realclean: clean
	find -name \*.jar -exec rm {} \;
todos:
	find -name \*.bat -exec todos {} \;
manifest:
	find -type f \! -name MANIFEST -exec md5sum {} \; > MANIFEST
check:
	find -type f \! -name MANIFEST -exec md5sum {} \; | diff - MANIFEST
zip: clean todos manifest
	(cd ..; zip -qr9 jEdit-`grep "CURRENT VERSION:" jEdit/VERSION|\
		awk '{print $$3}'`.zip jEdit)
	(cd ..; tar cfz jEdit-`grep "CURRENT VERSION:" jEdit/VERSION|\
		awk '{print $$3}'`.tgz jEdit)
include Rules.make
