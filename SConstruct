################################
# scons build system file
################################
#
# Most of this file can be used as a boilerplate build file.
# The only parts that are dependent on the details of the
# software package being compiled are the name of the tar file
# and the list of scons files to be run in the lower level
# directories.
# NOTE: to include other scons files, use the python command
#       execfile('fileName')
#
################################

# get operating system info
import os
import string
import SCons.Node.FS

os.umask(022)

# Software version
versionMajor = '12'
versionMinor = '0'

# determine the os and machine names
uname    = os.uname();
platform = uname[0]
machine  = uname[4]
osname   = platform + '-' +  machine

# Create an environment while importing the user's PATH.
# This allows us to get to the vxworks compiler for example.
# So for vxworks, make sure the tools are in your PATH
env = Environment(ENV = {'PATH' : os.environ['PATH']})

################################
# 64 or 32 bit operating system?
################################
    
# Define configure-type test function to
# see if operating system is 64 or 32 bits
def CheckHas64Bits(context, flags):
    context.Message( 'Checking for 64/32 bits ...' )
    lastCCFLAGS = context.env['CCFLAGS']
    lastLFLAGS  = context.env['LINKFLAGS']
    context.env.Append(CCFLAGS = flags, LINKFLAGS = flags)
    # (C) program to run to check for bits
    ret = context.TryRun("""
#include <stdio.h>
int main(int argc, char **argv) {
  printf(\"%d\", 8*sizeof(0L));
  return 0;
}
""", '.c')
    # restore original flags
    context.env.Replace(CCFLAGS = lastCCFLAGS, LINKFLAGS = lastLFLAGS)
    # if program successfully ran ...
    if ret[0]:
        context.Result(ret[1])
        if ret[1] == '64':
            return 64
        return 32
    # else if program did not run ...
    else:
        # Don't know if it's a 32 or 64 bit operating system
        context.Result('failed')
        return 0

# How many bits is the operating system?
# For Linux 64 bit x86 machines, the "machine' variable is x86_64,
# but for Darwin or Solaris there is no obvious check so run
# a configure-type test.
is64bits = False
if platform == 'Linux':
    if machine == 'x86_64':
        is64bits = True
elif platform == 'Darwin' or platform == 'SunOS':
    ccflags = '-xarch=amd64'
    if platform == 'Darwin':
        ccflags = '-arch x86_64'
    # run the test
    conf = Configure( env, custom_tests = { 'CheckBits' : CheckHas64Bits } )
    ret = conf.CheckBits(ccflags)
    env = conf.Finish()
    if ret < 1:
        print 'Cannot run test, assume 32 bit system'
    elif ret == 64:
        print 'Found 64 bit system'
        is64bits = True;
    else:
        print 'Found 32 bit system'


#########################################
# Command line options (try scons -h)
#########################################

Help('\n-D                  build from subdirectory of package\n')

# debug option
AddOption('--dbg',
           dest='ddebug',
           default=False,
           action='store_true')
debug = GetOption('ddebug')
print "debug =", debug
Help('\nlocal scons OPTIONS:\n')
Help('--dbg               compile with debug flag\n')

# vxworks option
AddOption('--vx',
           dest='doVX',
           default=False,
           action='store_true')
useVxworks = GetOption('doVX')
print "useVxworks =", useVxworks
Help('--vx                cross compile for vxworks\n')

# 32 bit option
AddOption('--32bits',
           dest='use32bits',
           default=False,
           action='store_true')
use32bits = GetOption('use32bits')
print "use32bits =", use32bits
Help('--32bits            compile 32bit libs & executables on 64bit system\n')

# install directory option
AddOption('--prefix',
           dest='prefix',
           nargs=1,
           default='',
           action='store')
prefix = GetOption('prefix')
Help('--prefix=<dir>      use base directory <dir> when doing install\n')

# uninstall option
AddOption('--uninstall',
           dest='uninstall',
           default=False,
           action='store_true')
uninstall = GetOption('uninstall')
Help('--uninstall         uninstall libs, headers, & examples\n')
Help('-c  install         uninstall libs, headers, examples, and remove all generated files\n')


#########################
# Compile flags
#########################

# debug/optimization flags
debugSuffix = ''
if debug:
    debugSuffix = '-dbg'
    # compile with -g and add debugSuffix to all executable names
    env.Append(CCFLAGS = '-g', PROGSUFFIX = debugSuffix)

elif platform == 'SunOS':
    env.Append(CCFLAGS = '-xO3')

else:
    env.Append(CCFLAGS = '-O3')

vxInc = ''
execLibs = ['']

# If using vxworks
if useVxworks:
    print "\nDoing vxworks\n"
    osname = platform + '-vxppc'

    if platform == 'Linux':
        vxbase = os.getenv('WIND_BASE', '/site/vxworks/5.5/ppc')
        vxbin = vxbase + '/x86-linux/bin'
    elif platform == 'SunOS':
        vxbase = os.getenv('WIND_BASE', '/site/vxworks/5.5/ppc')
        print "WIND_BASE = ", vxbase
        vxbin = vxbase + '/sun4-solaris/bin'
        if machine == 'i86pc':
            print '\nVxworks compilation not allowed on x86 solaris\n'
            raise SystemExit
    else:
        print '\nVxworks compilation not allowed on ' + platform + '\n'
        raise SystemExit
                    
    env.Replace(SHLIBSUFFIX = '.o')
    # get rid of -shared and use -r
    env.Replace(SHLINKFLAGS = '-r')
    # redefine SHCFLAGS/SHCCFLAGS to get rid of -fPIC (in Linux)
    env.Replace(SHCFLAGS = '-fno-for-scope -fno-builtin -fvolatile -fstrength-reduce -mlongcall -mcpu=604')
    env.Replace(SHCCFLAGS = '-fno-for-scope -fno-builtin -fvolatile -fstrength-reduce -mlongcall -mcpu=604')
    env.Append(CFLAGS = '-fno-for-scope -fno-builtin -fvolatile -fstrength-reduce -mlongcall -mcpu=604')
    env.Append(CCFLAGS = '-fno-for-scope -fno-builtin -fvolatile -fstrength-reduce -mlongcall -mcpu=604')
    env.Append(CPPPATH = vxbase + '/target/h')
    env.Append(CPPDEFINES = ['CPU=PPC604', 'VXWORKS', '_GNU_TOOL', 'VXWORKSPPC', 'POSIX_MISTAKE'])
    env['CC']     = 'ccppc'
    env['CXX']    = 'g++ppc'
    env['SHLINK'] = 'ldppc'
    env['AR']     = 'arppc'
    env['RANLIB'] = 'ranlibppc'
    use32bits = True

# else if NOT using vxworks
else:
    # platform dependent quantities
    execLibs = ['m', 'pthread', 'dl', 'rt']  # default to standard Linux libs
    if platform == 'SunOS':
        env.Append(CCFLAGS = '-mt')
        env.Append(CPPDEFINES = ['_GNU_SOURCE', '_REENTRANT', '_POSIX_PTHREAD_SEMANTICS', 'SunOS'])
        execLibs = ['m', 'posix4', 'pthread', 'socket', 'resolv', 'nsl', 'dl']
        if is64bits and not use32bits:
            if machine == 'sun4u':
                env.Append(CCFLAGS = '-xarch=native64 -xcode=pic32',
                           #LIBPATH = '/lib/64', # to do this we need to pass LIBPATH to lower level
                           LINKFLAGS = '-xarch=native64 -xcode=pic32')
            else:
                env.Append(CCFLAGS = '-xarch=amd64',
                           #LIBPATH = ['/lib/64', '/usr/ucblib/amd64'],
                           LINKFLAGS = '-xarch=amd64')
    
    elif platform == 'Darwin':
        execLibs = ['pthread', 'dl']
        env.Append(CPPDEFINES = 'Darwin', SHLINKFLAGS = '-multiply_defined suppress -flat_namespace -undefined suppress')
        env.Append(CCFLAGS = '-fmessage-length=0')
        if is64bits and not use32bits:
            env.Append(CCFLAGS = '-arch x86_64',
                       LINKFLAGS = '-arch x86_64 -Wl,-bind_at_load')
    
    elif platform == 'Linux':
        if is64bits and use32bits:
            env.Append(CCFLAGS = '-m32', LINKFLAGS = '-m32')
    
    if not is64bits and not use32bits:
        use32bits = True

if is64bits and use32bits:
    osname = osname + '-32'

print "OSNAME = ", osname

# hidden sub directory into which variant builds go
archDir = '.' + osname + debugSuffix

#########################
# Install stuff
#########################

# Any user specifed command line installation path overrides default
if prefix == '':
    # determine install directories since nothing on command line
    codaDirEnv    = os.getenv('CODA_HOME',"")
    installDirEnv = os.getenv('INSTALL_DIR', "")    
    if installDirEnv == "":
        if codaDirEnv == "":
            print "Need to define either CODA_HOME or INSTALL_DIR"
            raise SystemExit
        else:
            prefix = codaDirEnv
    else:
        prefix = installDirEnv
    print "Default install directory = ", prefix
else:
    print 'Cmdline install directory = ', prefix

# set our install directories
libDir = prefix + "/" + osname + '/lib'
binDir = prefix + "/" + osname + '/bin'
incDir = prefix + '/include'
print 'binDir = ', binDir
print 'libDir = ', libDir
print 'incDir = ', incDir

# use "install" on command line to install libs & headers
Help('install             install libs & headers\n')

# use "uninstall" on command line to uninstall libs, headers, and executables
#Help('uninstall           uninstall everything that was installed\n')

# use "examples" on command line to install executable examples
Help('examples            install executable examples\n')

# not necessary to create install directories explicitly
# (done automatically during install)

#########################
# Tar file
#########################

# Function that does the tar. Note that tar on Solaris is different
# (more primitive) than tar on Linux and MacOS. Solaris tar has no -z option
# and the exclude file does not allow wildcards. Thus, stick to Linux for
# creating the tar file.
def tarballer(target, source, env):
    if platform == 'SunOS':
        print '\nMake tar file from Linux or MacOS please\n'
        return
    dirname = os.path.basename(os.path.abspath('.'))
    cmd = 'tar -X tar/tarexclude -C .. -c -z -f ' + str(target[0]) + ' ./' + dirname
    p = os.popen(cmd)
    return p.close()

# name of tarfile (software package dependent)
tarfile = 'tar/et-' + versionMajor + '.' + versionMinor + '.tgz'

# tarfile builder
tarBuild = Builder(action = tarballer)
env.Append(BUILDERS = {'Tarball' : tarBuild})
env.Alias('tar', env.Tarball(target = tarfile, source = None))

# use "tar" on command line to create tar file
Help('tar                 create tar file (in ./tar)\n')

######################################################
# Lower level scons files (software package dependent)
######################################################

# make available to lower level scons files
Export('env incDir libDir binDir archDir execLibs tarfile debugSuffix')

# run lower level build files

# for vxworks only make libs and examples
if useVxworks:
    env.SConscript('src/libsrc/SConscript.vx',   variant_dir='src/libsrc/'+archDir,   duplicate=0)
    env.SConscript('src/examples/SConscript.vx', variant_dir='src/examples/'+archDir, duplicate=0)
else:
    env.SConscript('src/libsrc/SConscript',   variant_dir='src/libsrc/'+archDir,   duplicate=0)
    env.SConscript('src/examples/SConscript', variant_dir='src/examples/'+archDir, duplicate=0)

#########################
# Uninstall stuff
#########################

# This needs to be AFTER reading the scons files
# since now we know what the installed files are.
if uninstall:
    for fileName in env.FindInstalledFiles():
        Execute(Delete(fileName))
